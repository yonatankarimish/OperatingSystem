package com.sixsense.services;

import com.sixsense.model.commands.Operation;
import com.sixsense.model.commands.ParallelWorkflow;
import com.sixsense.model.events.AbstractEngineEvent;
import com.sixsense.model.events.EngineEventType;
import com.sixsense.model.events.IEngineEventHandler;
import com.sixsense.model.events.OperationEndEvent;
import com.sixsense.model.logic.*;
import com.sixsense.model.retention.OperationResult;
import com.sixsense.threading.ThreadingManager;
import com.sixsense.utillity.LogicalExpressionResolver;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkflowManager implements IEngineEventHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowManager.class);
    private final SessionEngine sessionEngine;
    private final DiagnosticManager diagnosticManager;
    private final ThreadingManager threadingManager;

    private final Map<String, ParallelWorkflow> parentWorkflows = new ConcurrentHashMap<>(); //key: operation id, value: parent workflow

    @Autowired
    private WorkflowManager(SessionEngine sessionEngine, DiagnosticManager diagnosticManager, ThreadingManager threadingManager) {
        this.sessionEngine = sessionEngine;
        this.diagnosticManager = diagnosticManager;
        this.threadingManager = threadingManager;

        this.diagnosticManager.registerHandler(this, EnumSet.of(EngineEventType.OperationEnd));
    }

    //TODO: currently returns a list of operations and their operation result. Shouldn't we return the workflow result instead?
    public CompletableFuture<Map<String, OperationResult>> executeWorkflow(ParallelWorkflow workflow){
        //First, verify the parallel node matches it's execution conditions
        boolean executionConditionsMet = LogicalExpressionResolver.resolveLogicalExpression(
            workflow.getDynamicFields(),
            workflow.getExecutionCondition()
        ).isResolved();

        if(executionConditionsMet) {
            //If conditions are met, submit the operations for execution
            Map<String, CompletableFuture<OperationResult>> runningOperations = new HashMap<>();
            for(Operation operation : workflow.getParallelOperations()){
                runningOperations.put(operation.getUUID(), this.executeParallelOperation(operation));
            }

            return CompletableFuture.allOf(
                //Then wait for all operations to finish asynchronously
                runningOperations.values().toArray(CompletableFuture[]::new)
            ).thenApply(voidStub -> {
                //and map each operation id to the operation result
                Map<String, OperationResult> resultMap = new HashMap<>();
                for(Map.Entry<String, CompletableFuture<OperationResult>> operationResult : runningOperations.entrySet()){
                    String operationId = operationResult.getKey();
                    CompletableFuture<OperationResult> result = operationResult.getValue();
                    resultMap.put(operationId, result.join());
                }
                return resultMap;
            });
        }else{
            //Otherwise, skip the execution of the operations
            Map<String, OperationResult> emptyResult = new HashMap<>();
            for(Operation operation : workflow.getParallelOperations()){
                emptyResult.put(operation.getUUID(), new OperationResult().withExpressionResult(ExpressionResult.skip()));
            }

            return CompletableFuture.completedFuture(emptyResult);
        }
    }

    private CompletableFuture<OperationResult> executeParallelOperation(Operation operation){
        try {
            return threadingManager.submit(() -> sessionEngine.executeOperation(operation));
        }catch (Exception e){
            logger.error("Failed to submit operation " + operation.getUUID() + " to worker queue. Caused by: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void handleEngineEvent(AbstractEngineEvent event) {
        /*We listen to the diagnostic manager operation end events without waiting for the next workflow to complete
         * Since workflows are lengthy operations, this is a major time and resource saver*/
        try {
            OperationEndEvent operationEndEvent = (OperationEndEvent)event;
            notifyWorkflow(operationEndEvent.getOperation(), operationEndEvent.getResult());
        }catch (ClassCastException e){
            logger.error("Failed to notify workflow manager that an operation has completed. Caused by: " + e.getMessage());
        }
    }

    private void notifyWorkflow(Operation resolvedOperation, OperationResult operationOutcome){
        //Operations always use SELF_SEQUENCE_EAGER. Therefore, execute their sequential workflows
        resolvedOperation.setSequenceExecutionStarted(true);
        if(operationOutcome.getExpressionResult().getOutcome().equals(ResultStatus.SUCCESS)) {
            for (ParallelWorkflow sequence : resolvedOperation.getSequenceUponSuccess()) {
                executeWorkflow(sequence);
            }
        }else{
            for (ParallelWorkflow sequence : resolvedOperation.getSequenceUponFailure()) {
                executeWorkflow(sequence);
            }
        }

        //Operations always use PARENT_NOTIFICATION_EAGER. Therefore, if the operation is part of a parallel workflow, notify it's parent
        //Note that the container workflow will not be null only if the resolved operation was executed as part of a parallel workflow
        ParallelWorkflow container = this.parentWorkflows.get(resolvedOperation.getUUID());
        if (container != null) {
            notifyWorkflow(container, resolvedOperation, operationOutcome);
        }
    }

    private void notifyWorkflow(ParallelWorkflow parentWorkflow, Operation resolvedOperation, OperationResult operationOutcome){
        Set<WorkflowPolicy> workflowPolicies = parentWorkflow.getWorkflowPolicies();
        parentWorkflow.addOperationOutcome(resolvedOperation.getUUID(), operationOutcome);
        boolean operationSuccessful = operationOutcome.getExpressionResult().getOutcome().equals(ResultStatus.SUCCESS);
        boolean allChildrenCompleted = parentWorkflow.getCompletedOperations() == parentWorkflow.getTotalOperations();

        if(!parentWorkflow.isSequenceExecutionStarted()) { //to avoid executing the same sequence more than once per workflow
            if (operationSuccessful) {
                if (workflowPolicies.contains(WorkflowPolicy.SELF_SEQUENCE_EAGER) || allChildrenCompleted) {
                    executeSequence(parentWorkflow, parentWorkflow.getSequenceUponSuccess());
                }
            } else {
                if (workflowPolicies.contains(WorkflowPolicy.SELF_SEQUENCE_EAGER) || allChildrenCompleted) {
                    executeSequence(parentWorkflow, parentWorkflow.getSequenceUponFailure());
                }

                if (workflowPolicies.contains(WorkflowPolicy.OPERATIONS_DEPENDENT)) {
                    terminateParallelOperations(parentWorkflow);
                }
            }
        }
    }

    private void executeSequence(ParallelWorkflow parentWorkflow, List<ParallelWorkflow> sequence){
        parentWorkflow.setSequenceExecutionStarted(true);
        for (ParallelWorkflow child : sequence) {
            executeWorkflow(child);
        }
    }

    private void terminateParallelOperations(ParallelWorkflow parentWorkflow){
        try {
            for (Operation dependentOperation : parentWorkflow.getParallelOperations()) {
                if (!dependentOperation.isAlreadyExecuted()) {
                    threadingManager.submit(() -> sessionEngine.terminateOperation(dependentOperation.getUUID()));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to remove running operations from worker queue. Caused by: " + e.getMessage());
        }
    }
}

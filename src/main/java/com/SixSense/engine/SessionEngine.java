package com.SixSense.engine;

import com.SixSense.data.Outcomes.ExpectedOutcome;
import com.SixSense.data.Outcomes.ResultStatus;
import com.SixSense.data.commands.Operation;
import com.SixSense.data.commands.Block;
import com.SixSense.data.commands.Command;
import com.SixSense.data.commands.ICommand;
import com.SixSense.io.Session;
import com.SixSense.util.MessageLiterals;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.concurrent.*;

public class SessionEngine {
    private static Logger logger = Logger.getLogger(SessionEngine.class);
    private ProcessBuilder builder = new ProcessBuilder();
    private static SessionEngine engineInstance;
    private final ExecutorService workerPool = Executors.newCachedThreadPool();

    private SessionEngine(){}

    public static synchronized SessionEngine getInstance(){
        if(engineInstance == null){
            engineInstance = new SessionEngine();
            logger.info("SessionEngine Created");
        }
        return engineInstance;
    }

    public ExpectedOutcome executeOperation(Operation engineOperation) {
        if(engineOperation == null || engineOperation.getExecutionBlock() == null){
            return ExpectedOutcome.defaultOutcome();
        }

        try (Session session = this.createSession()) {
            ICommand executionBlock = engineOperation.getExecutionBlock();
            return this.executeBlock(session, executionBlock);
        }catch (IOException e){
            logger.error("SessionEngine - Failed to execute operation " + engineOperation.getFullOperationName() + ". Caused by: ", e);
            return ExpectedOutcome.executionError("SessionEngine - Failed to execute operation " + engineOperation.getFullOperationName() + ". Caused by: " + e.getMessage());
        }
    }

    private ExpectedOutcome executeBlock(Session session, ICommand executionBlock) throws IOException{
        if (executionBlock instanceof Command) {
            return this.executeCommand(session, (Command)executionBlock);
        }else if(executionBlock instanceof Block){
            Block parentBlock = (Block)executionBlock;
            ExpectedOutcome progressiveResult = ExpectedOutcome.defaultOutcome();

            while(!parentBlock.hasExhaustedCommands()){
                Command nextCommand = parentBlock.getNextCommand();
                if(nextCommand != null){
                    progressiveResult = this.executeCommand(session, nextCommand);
                    if(progressiveResult.getOutcome().equals(ResultStatus.FAILURE)){
                        return  progressiveResult;
                    }
                }
            }

            return progressiveResult;
        }else{
            return ExpectedOutcome.executionError(MessageLiterals.InvalidExecutionBlock);
        }
    }

    private ExpectedOutcome executeCommand(Session session, Command currentCommand) throws IOException{
        return session.executeCommand(currentCommand);
    }

    private Session createSession() throws IOException{
        Process process = builder.command("/bin/bash").start();

        Session session = new Session(process);
        Future<Boolean> sessionOutput = workerPool.submit(session.getProcessOutput());
        Future<Boolean> sessionErrors = workerPool.submit(session.getProcessErrors());

        return session;
    }
}

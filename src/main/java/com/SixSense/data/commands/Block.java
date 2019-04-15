package com.SixSense.data.commands;

import com.SixSense.data.logic.ExecutionCondition;
import com.SixSense.data.logic.ExpectedOutcome;
import com.SixSense.data.logic.LogicalCondition;
import com.SixSense.util.CommandUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Block extends AbstractCommand implements ICommand {
    private int repeatLimit;
    private int repeatCount;
    private List<ICommand> childBlocks;

    private Iterator<ICommand> commandIterator;
    private ICommand currentCommand;

    /*Try not to pollute with additional constructors
     * The empty constructor is for using the 'with' design pattern
     * The parameterized constructor is for conditions and results only */
    public Block() {
        super();
        this.repeatCount = 0;
        this.childBlocks = new ArrayList<>();
    }

    public Block(int repeatLimit, List<ICommand> childBlocks, List<ExecutionCondition> executionConditions, LogicalCondition conditionAggregation, List<ExpectedOutcome> expectedOutcomes, LogicalCondition outcomeAggregation, String aggregatedOutcomeMessage) {
        super(executionConditions, conditionAggregation, expectedOutcomes, outcomeAggregation, aggregatedOutcomeMessage);
        if(repeatLimit < 0){
            throw new IllegalArgumentException("Cannot construct Block with a negative repeat count");
        } else {
            this.repeatCount = 0;
            this.repeatLimit = repeatLimit;
            this.childBlocks = childBlocks;
        }
    }

    public Block addCommand(Command childCommand){
        return CommandUtils.addCommand(this, childCommand);
    }

    public Block addBlock(Block childCommand){
        return CommandUtils.addBlock(this, childCommand);
    }

    public ICommand chainCommands(ICommand additional){
        return CommandUtils.chainCommands(this, additional);
    }

    public ICommand getNextCommand(){
        //If no iterator has been created, obtain an iterator from the childBlocks list
        Iterator<ICommand> commandIterator = this.getCommandIterator();

        //If the last available command has been returned, return null
        if(hasExhaustedCommands()){
            return null;
        }

        //If not returned by this step, there is a next command available
        //If the last command was a plain command, or already executed, obtain the next command.
        if(this.currentCommand == null || this.currentCommand.isAlreadyExecuted()){
            this.currentCommand = commandIterator.next();
        }

        //finally, return the next command which has not been executed
        return this.currentCommand;

    }

    public boolean hasExhaustedCommands(){
        if(!this.getCommandIterator().hasNext()){
            if(this.currentCommand == null){
                return true;
            }else if(!this.currentCommand.isAlreadyExecuted()){
                return false;
            }else if(repeatCount < repeatLimit) {
                //If the passed block is a repeating block, which has not yet finished repeating, reset the current loop and return false
                this.resetNextCommandLoop();
                return false;
            }
            return true;
        }
        return false;
    }

    private void resetNextCommandLoop(){
        this.repeatCount++;
        this.commandIterator = this.getCommandIterator();

        for(ICommand command : this.getChildBlocks()){
            command.setAlreadyExecuted(false);
            if(command instanceof Block){
                ((Block) command).resetNextCommandLoop();
                ((Block) command).repeatCount = 0;
            }
        }
    }

    private Iterator<ICommand> getCommandIterator(){
        if(this.commandIterator == null){
            this.commandIterator = childBlocks.iterator();
        }
        return this.commandIterator;
    }

    public int getRepeatLimit() {
        return repeatLimit;
    }

    public void setRepeatLimit(int repeatLimit) {
        if(repeatLimit < 0) {
            throw new IllegalArgumentException("Cannot construct Block with a negative repeat count");
        } else {
            this.repeatLimit = repeatLimit;
        }
    }

    public Block withRepeatLimit(int repeatLimit) {
        if(repeatLimit < 0) {
            throw new IllegalArgumentException("Cannot construct Block with a negative repeat count");
        } else {
            this.repeatLimit = repeatLimit;
            return this;
        }
    }

    public List<ICommand> getChildBlocks() {
        return Collections.unmodifiableList(childBlocks);
    }

    public Block prependChildBlock(ICommand childBlock) {
        this.childBlocks.add(0, childBlock);
        return this;
    }

    public Block addChildBlock(ICommand childBlock) {
        this.childBlocks.add(childBlock);
        return this;
    }

    public Block addChildBlocks(List<ICommand> childBlocks) {
        this.childBlocks.addAll(childBlocks);
        return this;
    }

    @Override
    public String toString() {
        return "Block{" +
                "repeatLimit=" + repeatLimit +
                ", childBlocks=" + childBlocks +
                ", commandIterator=" + commandIterator +
                ", currentCommand=" + currentCommand +
                ", alreadyExecuted=" + alreadyExecuted +
                ", executionConditions=" + executionConditions +
                ", conditionAggregation=" + conditionAggregation +
                ", expectedOutcomes=" + expectedOutcomes +
                ", outcomeAggregation=" + outcomeAggregation +
                ", aggregatedOutcomeMessage='" + aggregatedOutcomeMessage + '\'' +
                ", dynamicFields=" + dynamicFields +
                ", saveTo=" + saveTo +
                '}';
    }
}

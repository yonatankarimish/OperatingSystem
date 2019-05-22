package com.SixSense.data.logic;

public class ExpressionResult {
    private boolean resolved; //Did the logical expression turn out to be true?
    private ResultStatus outcome; //If the logical expression has been resolved, what should be the outcome of the command?
    private String message; //Arbitrary message, if the logical expression has been resolved

    public ExpressionResult() {
        this.resolved = false;
        this.outcome = ResultStatus.SUCCESS;
        this.message = "";
    }

    public ExpressionResult(boolean resolved, ResultStatus outcome, String message) {
        this.resolved = resolved;
        this.outcome = outcome;
        this.message = message;
    }

    public static ExpressionResult defaultOutcome(){
        return new ExpressionResult()
                .withResolved(true)
                .withOutcome(ResultStatus.SUCCESS)
                .withMessage("");
    }

    public static ExpressionResult skip(){
        return new ExpressionResult()
                .withResolved(true)
                .withOutcome(ResultStatus.SKIP)
                .withMessage("");
    }

    public static ExpressionResult executionError(String errorMessage){
        return new ExpressionResult()
                .withResolved(false)
                .withOutcome(ResultStatus.FAILURE)
                .withMessage(errorMessage);
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public ExpressionResult withResolved(boolean resolved) {
        this.resolved = resolved;
        return this;
    }

    public ResultStatus getOutcome() {
        return outcome;
    }

    public void setOutcome(ResultStatus outcome) {
        this.outcome = outcome;
    }

    public ExpressionResult withOutcome(ResultStatus resultStatus) {
        this.outcome = resultStatus;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ExpressionResult withMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        else if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return equals((ExpressionResult) other);
    }

    public boolean equals(ExpressionResult otherOutcome) {
        return this.resolved == otherOutcome.resolved
                && this.outcome.equals(otherOutcome.outcome);
    }

    public boolean strongEquals(ExpressionResult otherOutcome) {
        return equals(otherOutcome)
                && this.message.equals(otherOutcome.message);
    }

    //Returns a new instance of the same expected outcome in its pristine state. That is - as if the new state was never resolved
    public ExpressionResult deepClone(){
        return new ExpressionResult()
                .withResolved(false)
                .withOutcome(this.outcome)
                .withMessage(this.message);
    }

    @Override
    public String toString() {
        return "ExpressionResult{" +
                "resolved=" + resolved +
                ", outcome=" + outcome +
                ", message='" + message + '\'' +
                '}';
    }
}

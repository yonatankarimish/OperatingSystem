package com.sixsense.model.logic;

public enum LogicalCondition {
    AND, OR, NAND, NOR;

    public boolean isAggregating(){
        return this.equals(AND) || this.equals(NOR);
    }
}

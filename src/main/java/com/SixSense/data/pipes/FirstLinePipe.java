package com.SixSense.data.pipes;

import com.SixSense.io.Session;
import com.SixSense.util.MessageLiterals;

import java.util.List;

public class FirstLinePipe extends AbstractOutputPipe {

    public FirstLinePipe(){}

    @Override
    public String pipe(Session session, String output) {
        if(output == null || output.isEmpty()){
            return output;
        }

        int lineBreakIndex = output.stripLeading().indexOf(MessageLiterals.LineBreak);
        if(lineBreakIndex < 0){
            return output;
        }

        return output.stripLeading().substring(0, lineBreakIndex);
    }

    @Override
    public List<String> pipe(Session session, List<String> output) {
        if(output == null || output.isEmpty()){
            return output;
        }

        return output.subList(0, 1);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FirstLinePipe;
    }
}
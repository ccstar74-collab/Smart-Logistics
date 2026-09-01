package com.smartlogistics.agent;

import java.util.Collections;
import java.util.Map;

final class ToolSelection {
    final String intent;
    final double confidence;
    final Map<String,Object> parameters;
    final boolean needsClarification;
    final String clarificationQuestion;

    ToolSelection(String intent, double confidence, Map<String,Object> parameters,
                  boolean needsClarification, String clarificationQuestion) {
        this.intent = intent;
        this.confidence = confidence;
        this.parameters = parameters == null ? Collections.emptyMap() : parameters;
        this.needsClarification = needsClarification;
        this.clarificationQuestion = clarificationQuestion == null ? "" : clarificationQuestion;
    }
}

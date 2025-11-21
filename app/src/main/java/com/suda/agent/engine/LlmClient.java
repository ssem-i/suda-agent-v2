package com.suda.agent.engine;

public class LlmClient {
    static { System.loadLibrary("Llama2Genie"); }

    public LlmClient() {};

    public native int Init(String modelPath);
    public native void UnInit();
    public native String Infer(String strPrompt);
}

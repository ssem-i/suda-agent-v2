#include <jni.h>

#include <string>

#include "Logging.hpp"

#include "main.hpp"

DsDialog g_dialog;

// 경로를 직접 전달받는 Init 함수
extern "C" JNIEXPORT jint JNICALL Java_com_suda_agent_engine_LlmClient_Init(JNIEnv *env, jobject thiz, jstring jstrModelPath)
{
	DsConfig config;

	const char *modelPath = env->GetStringUTFChars(jstrModelPath, nullptr);
	LoggingInfo("<<<<<<<<<< Call config.Init(\"%s\");\n", modelPath);

	int rc = config.Init((char *)modelPath);
	LoggingInfo(">>>>>>>>>> %d = config.Init(\"%s\");\n", rc, modelPath);

	env->ReleaseStringUTFChars(jstrModelPath, modelPath);

	if (rc == 0)
	{
		LoggingInfo("<<<<<<<<<< Call g_dialog.Init(config);\n");
		rc = g_dialog.Init(config);
		LoggingInfo(">>>>>>>>>> %d = g_dialog.Init(config);\n", rc);
	}

	return rc;
}

extern "C" JNIEXPORT void JNICALL Java_com_suda_agent_engine_LlmClient_UnInit(JNIEnv *env, jobject thiz)
{
	g_dialog.UnInit();
}

extern "C" JNIEXPORT jstring JNICALL Java_com_suda_agent_engine_LlmClient_Infer(JNIEnv *env, jobject thiz, jstring jstrPrompt)
{
	const char *szPrompt = env->GetStringUTFChars(jstrPrompt, nullptr);

	std::string strPrompt = std::string(szPrompt);

	LoggingMax("<<<<<<<<<< Call strAnswer = g_dialog.Query(\"%s\");\n", strPrompt.c_str());
	std::string strAnswer = g_dialog.Query(strPrompt);
	LoggingMax(">>>>>>>>>> Call g_dialog.Query(\"%s\");\n", strPrompt.c_str());
	LoggingError("strAnswer = [ %s ]\n", strAnswer.c_str());

	env->ReleaseStringUTFChars(jstrPrompt, szPrompt);

	return env->NewStringUTF(strAnswer.c_str());
}

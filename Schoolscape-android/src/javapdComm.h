/*
 * Copyright (c) 2021 Antoine Rousseau <antoine@metalu.net>
 * BSD Simplified License, see the file "LICENSE.txt" in this distribution.
  */
#pragma once
#include <jni.h>
#include "ofxPd.h"

void setupJava();

jobjectArray makeJavaArray(JNIEnv *env, const pd::List& list);
int dequeueToJava();
void queueToJava(const pd::List& list);

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_net_metalu_Schoolscape_OFActivity_sendToPd (JNIEnv *env, jclass, jobjectArray array);

#ifdef __cplusplus
}
#endif


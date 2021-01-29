/*
 * Copyright (c) 2021 Antoine Rousseau <antoine@metalu.net>
 * BSD Simplified License, see the file "LICENSE.txt" in this distribution.
 */

#include "javapdComm.h"
/*#include <ofTypes.h>*/
#include "pofBase.h"
#include <ofMain.h>
#include "ofxAndroidUtils.h"
#include "testApp.h"

using namespace std;
using namespace pd;

JNIEnv *env;
jclass objClass = NULL;
jclass floatClass = NULL;
jclass intClass = NULL;
jclass stringClass = NULL;
jmethodID floatInit = NULL;

/*jobject activity = NULL;
jclass activityClass = NULL;
jmethodID javaReceiveMessage = NULL;*/

ofMutex toJavaMutex;
queue<List> toJavaQueue;

const char Tag[]="javapdComm";

void setupJava(){
	ofLogNotice(Tag, "setup Java interface");
	if(objClass != NULL) return;
	
	env = ofGetJNIEnv();
	objClass = (jclass)env->NewGlobalRef(env->FindClass("java/lang/Object"));
	stringClass = (jclass)env->NewGlobalRef(env->FindClass("java/lang/String"));
	floatClass = (jclass)env->NewGlobalRef(env->FindClass("java/lang/Float"));
	intClass = (jclass)env->NewGlobalRef(env->FindClass("java/lang/Integer"));
	floatInit = env->GetMethodID(floatClass, "<init>", "(F)V");

	if((env == NULL) || (objClass == NULL) || (stringClass == NULL) || (floatClass == NULL) || (intClass == NULL) || (floatInit == NULL))
	{
		ofLogError(Tag, "java type classes references are NULL!");
	}

	/*activity = ofGetOFActivityObject();
	activityClass = env->FindClass("net/metalu/Schoolscape/OFActivity");
	javaReceiveMessage = env->GetMethodID(activityClass,"receiveMessage","([Ljava/lang/Object;)V");

	if(activity == NULL) ofLogError(Tag, "java activity is NULL!");
	if(activityClass == NULL) ofLogError(Tag, "java activityClass is NULL!");
	if(javaReceiveMessage == NULL) ofLogError(Tag, "java javaReceiveMessage is NULL!");*/
}


jobjectArray makeJavaArray(JNIEnv *env, const List& list) {
	jobjectArray jarray = env->NewObjectArray(list.len(), objClass, NULL);

	for(unsigned int i = 0; i < list.len(); ++i) {
		jobject obj = NULL;
		if(list.isFloat(i)) obj = env->NewObject(floatClass, floatInit, list.getFloat(i));
		else if(list.isSymbol(i)) obj = env->NewStringUTF(list.getSymbol(i).c_str());

		env->SetObjectArrayElement(jarray, i, obj);
		if (obj != NULL) {
		  env->DeleteLocalRef(obj);  // The reference in the array remains.
		}
	}
	return jarray;
}

void sendToJava(const List& list) {
	JNIEnv *env = ofGetJNIEnv();
	jobject activity = ofGetOFActivityObject();
	jclass activityClass = env->FindClass("net/metalu/Schoolscape/OFActivity");
	jmethodID javaReceiveMessage = env->GetMethodID(activityClass,"receiveMessage","([Ljava/lang/Object;)V");

	jobjectArray jarray = makeJavaArray(env, list);
	env->CallVoidMethod(activity, javaReceiveMessage, jarray);
	env->DeleteLocalRef(jarray);
}

int dequeueToJava() {
	List *list = NULL;
	toJavaMutex.lock();
	int n = toJavaQueue.size();
	if(n > 0) {
		list = &toJavaQueue.front();
		toJavaMutex.unlock();
		sendToJava(*list);
		n--;
		toJavaMutex.lock();
		toJavaQueue.pop();
	}
	toJavaMutex.unlock();
	return n;
}

void queueToJava(const List& list){
	toJavaMutex.lock();
	toJavaQueue.push(list);
	toJavaMutex.unlock();
}

float extractFloatJobj(JNIEnv *env, jobject obj)
{
	jmethodID method  = env->GetMethodID(floatClass, "floatValue", "()F");
	if(method == NULL) return 0.0;
	return env->CallFloatMethod(obj, method);
}

int extractIntJobj(JNIEnv *env, jobject obj)
{
	jmethodID method  = env->GetMethodID(floatClass, "intValue", "()I");
	if(method == NULL) return 0;
	return env->CallIntMethod(obj, method);
}

JNIEXPORT void JNICALL Java_net_metalu_Schoolscape_OFActivity_sendToPd (JNIEnv *env, jclass, jobjectArray array) {
	std::vector<Any> vec;
	unsigned int i, len = env->GetArrayLength(array);

	vec.push_back(string("SYSTEM"));

	for(i = 0 ; i < len ; i++ ) {
		jobject obj = env->GetObjectArrayElement(array, i);
		//jclass cls = env->GetObjectClass(obj);
		
		if(env->IsInstanceOf(obj, stringClass) == JNI_TRUE){
			const char * chr = env->GetStringUTFChars((jstring)obj, JNI_FALSE);
			vec.push_back(string(chr));
			env->ReleaseStringUTFChars((jstring)obj, chr);
		}
		else if(env->IsInstanceOf(obj, floatClass) == JNI_TRUE) {
			vec.push_back(extractFloatJobj(env, obj));
		}
		else if(env->IsInstanceOf(obj, intClass) == JNI_TRUE) {
			vec.push_back((float)extractIntJobj(env, obj));
		}
	}

	pofBase::sendToPd(vec);
	//ofLogNotice("testApp")<< "JavaToPd: " << vec.size() << " args.";
}

JNIEXPORT void JNICALL Java_net_metalu_Schoolscape_OFActivity_runAudio (JNIEnv *env, jclass, jobjectArray array) {
	((testApp*)ofGetAppPtr())->runAudio();
}


/*
 * Copyright (c) 2011 Dan Wilcox <danomatika@gmail.com>
 *
 * BSD Simplified License.
 * For information on usage and redistribution, and for a DISCLAIMER OF ALL
 * WARRANTIES, see the file, "LICENSE.txt," in this distribution.
 *
 * See https://github.com/danomatika/ofxPd for documentation
 *
 */
#include "ofMain.h"
#include "testApp.h"

//========================================================================

int main(){
	ofSetupOpenGL(1024, 768, OF_FULLSCREEN);
	ofRunApp(new testApp());
	return 0;
}

#ifdef TARGET_ANDROID
void ofAndroidApplicationInit()
{
    //application scope init
}

void ofAndroidActivityInit()
{
	//activity scope init
	main();
}
#endif

/*

#ifdef TARGET_ANDROID
#include <jni.h>

//========================================================================
extern "C"{
	void Java_cc_openframeworks_OFAndroid_init( JNIEnv*  env, jobject  thiz ){
		main();
	}
}
#endif*/

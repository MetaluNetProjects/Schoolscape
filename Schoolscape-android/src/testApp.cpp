/*
 * Copyright (c) 2014 Antoine Rousseau <antoine@metalu.net>
 * BSD Simplified License, see the file "LICENSE.txt" in this distribution.
 * See https://github.com/Ant1r/ofxPof for documentation and updates.
 */

#include "testApp.h"
#include "pofBase.h"
#include "ofxAccelerometer.h"
#include "abl_link/abl_link~.hpp"
#include "EventDispatcher.h"
#include "javapdComm.h"

using namespace std;

// externals setup declarations :
extern "C" {
    extern void limiter_tilde_setup();
    extern void z_tilde_setup(void);
    extern void seq_setup(void);
    extern void seq_setup(void);
    extern void midiparse_setup(void);
    extern void atan_tilde_setup();
    extern void ssaw_tilde_setup(void);
    extern void ndmetro_setup(void);
    extern void mknob_setup(void);
    extern void tabenv_setup(void);
    extern void dispatch_setup(void);
    extern void fdn_tilde_setup(void);
    extern void dist_tilde_setup(void);
    extern void filterortho_tilde_setup(void);
    extern void f2s_setup(void);
    extern void filter_tilde_setup(void);
    extern void scale_setup(void);
    extern void shuffle_setup(void);
    extern void iem_receive_setup(void);
}


const char Tag[]="Schoolscape";

//--------------------------------------------------------------
int ticksPerBuffer = 1; // 8 * 64 = buffer len of 512
int numInputs = 1;
int sampleRate = 44100;

void testApp::setup() {

	setupJava();

	ofxAccelerometer.setup();
	
	ofLogNotice(Tag, "init pd");
	if(!puda.init(2, numInputs, sampleRate, ticksPerBuffer)) {
		ofExit();
	}
	
	puda.subscribe("toSYSTEM");
	// add message receiver, required if you want to receive messages
	puda.addReceiver(*this); // automatically receives from all subscribed sources

	ofLogNotice(Tag, "init pof");
	pofBase::setup();
	
	ofLogNotice(Tag, "start pd");
	puda.start();
	
	// ------------ load externals -----------------
	
	limiter_tilde_setup();
	z_tilde_setup();
    seq_setup();
    midiparse_setup();
    atan_tilde_setup();
	abl_link_tilde_setup();
	ssaw_tilde_setup();
	ndmetro_setup();
	mknob_setup();
	tabenv_setup();
	f2s_setup();
	dispatch_setup();
	fdn_tilde_setup();
	dist_tilde_setup();
	filterortho_tilde_setup();
	filter_tilde_setup();
	scale_setup();
	shuffle_setup();
	iem_receive_setup();

	if(ofxAndroidCheckPermission(OFX_ANDROID_PERMISSION_RECORD_AUDIO)) runAudio();
	ofxAndroidRequestPermission(OFX_ANDROID_PERMISSION_RECORD_AUDIO);
}

void testApp::runAudio() {

	ofLogNotice(Tag, "init sound");
//     Ask for permission to record audio,
//     not needed if no in channels used
	if(! ofxAndroidCheckPermission(OFX_ANDROID_PERMISSION_RECORD_AUDIO)) {
		numInputs = 0;
		libpd_init_audio(numInputs, 2, sampleRate);
	}
	
	//ofxAndroidRequestPermission(OFX_ANDROID_PERMISSION_WRITE_EXTERNAL_STORAGE);
	// setup OF sound stream
	//ofSoundStreamSetup(2, numInputs, this, 44100, ofxPd::blockSize()*ticksPerBuffer, 4);
	os = NULL;
	os = opensl_open(sampleRate, numInputs, 2, ticksPerBuffer*PdBase::blockSize(), testApp::opensl_process, (void*)this);
	if(os == NULL) ofLogError(Tag, "error opening opensl");

	// ---------------------------------------------
	
	ofLogNotice(Tag, "load patch");
	Patch patch = puda.openPatch(ofToDataPath("pd/pof_main.pd", true));
	
	puda.sendSymbol("datadir", ofToDataPath("pd/data", true));

	if(os) opensl_start(os);
}

//--------------------------------------------------------------
void testApp::update() {
	pofBase::updateAll();
	while(dequeueToJava()>0);
}

//--------------------------------------------------------------
void testApp::draw() {
	pofBase::drawAll();
}

//--------------------------------------------------------------
//--------------------------------------------------------------
void testApp::exit() {
	ofLogNotice(Tag, "exit");
	puda.stop();
	if(os) opensl_close(os);
	//pofBase::release();
	ofExit();
}

//--------------------------------------------------------------
void testApp::keyPressed(int key) {}

//--------------------------------------------------------------
bool testApp::backPressed(){
	ofLogNotice("testApp", "back pressed");
	//ofNotifyExit();//exit();
	pofBase::backPressed();
	return true;
}

//--------------------------------------------------------------
void testApp::touchDown(int x, int y, int id){
	pofBase::touchDownAll(x, y, id);
}

void testApp::touchMoved(int x, int y, int id){
	pofBase::touchMovedAll(x, y, id);	
}

void testApp::touchUp(int x, int y, int id){
	pofBase::touchUpAll(x, y, id);
}

//--------------------------------------------------------------
void testApp::windowResized(int w, int h) {
	pofBase::windowResized(w,h);
}

//--------------------------------------------------------------
void testApp::audioReceived(float * input, int bufferSize, int nChannels) {
	puda.audioIn(input, bufferSize, nChannels);
}

//--------------------------------------------------------------
void testApp::audioRequested(float * output, int bufferSize, int nChannels) {
	puda.audioOut(output, bufferSize, nChannels);
}

//--------------------------------------------------------------
void testApp::reloadTextures() {
	pofBase::reloadTextures();
}

void testApp::unloadTextures() {
	pofBase::unloadTextures();
}

//--------------------------------------------------------------
void testApp::print(const std::string& message) {
//	cout << message << endl;
	ofLogNotice(Tag) << message << endl;
}

//--------------------------------------------------------------
void testApp::receiveBang(const std::string& dest) {
	cout << "OF: bang " << dest << endl;
}

void testApp::receiveFloat(const std::string& dest, float value) {
	cout << "OF: float " << dest << ": " << value << endl;
}

void testApp::receiveSymbol(const std::string& dest, const std::string& symbol) {
	cout << "OF: symbol " << dest << ": " << symbol << endl;
}

void testApp::receiveList(const std::string& dest, const List& list) {
	cout << "OF: list " << dest << ": ";

	// step through the list
	/*for(int i = 0; i < list.len(); ++i) {
		if(list.isFloat(i))
			cout << list.getFloat(i) << " ";
		else if(list.isSymbol(i))
			cout << list.getSymbol(i) << " ";
	}

	// you can also use the built in toString function or simply stream it out
	// cout << list.toString();
	// cout << list;

	// print an OSC-style type string
	cout << list.types() << endl;*/
}

void testApp::receiveMessage(const std::string& dest, const std::string& msg, const List& list) {
	ofLogNotice(Tag) << "message: " << dest << ": " << msg << " " << list.toString() << list.types() << endl;
	if(msg == "quit") {
		//puda.stop();
		//if(os) opensl_close(os);
		pofBase::dispatcher.waitForThread(true);
		clock_unset(pofBase::queueClock);
		std::exit(0); //testApp::exit(); //exit();
	}

	List *newlist = new List();
	*newlist << dest << msg;
	
	for(unsigned int i = 0; i < list.len(); ++i) {
		if(list.isFloat(i))
			*newlist << list.getFloat(i);
		else if(list.isSymbol(i))
			*newlist << list.getSymbol(i);
	}

	queueToJava(*newlist);
}

//--------------------------------------------------------------
//--------------------------------------------------------------
short testInBuf[1024], testOutBuf[1024];
void testApp::opensl_process(void *app, int sample_rate, int buffer_frames,
			int input_channels, const short *input_buffer,
			int output_channels, short *output_buffer) {
	((testApp*)app)->puda.PdBase::processShort(/*buffer_frames*/1,(short *)input_buffer, output_buffer);
}

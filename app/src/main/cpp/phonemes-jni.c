// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <espeak-ng/speak_lib.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static char * current_path = NULL;
static void error(JNIEnv *env, const char *message) {
    if (!(*env)->ExceptionCheck(env)) (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalStateException"),message);
}
static char * utf8(JNIEnv *env, jbyteArray bytes) {
    if (!bytes) { error(env,"Brak danych fonetycznych"); return NULL; }
    jsize size=(*env)->GetArrayLength(env,bytes);
    if (size < 1 || size > 16384) { error(env,"Nieprawidłowa długość tekstu"); return NULL; }
    char *text=malloc((size_t)size+1);
    if (!text) { error(env,"Za mało pamięci"); return NULL; }
    (*env)->GetByteArrayRegion(env,bytes,0,size,(jbyte*)text); text[size]=0;
    if (memchr(text,0,(size_t)size)) { free(text); error(env,"Nieprawidłowy znak w tekście"); return NULL; }
    return text;
}
JNIEXPORT void JNICALL Java_pl_ergenos_glosfacet_PolishPhonemizer_initialize(JNIEnv *env,jobject self,jbyteArray path) {
    (void)self;char *directory=utf8(env,path);if(!directory)return;
    pthread_mutex_lock(&mutex);
    if (!current_path || strcmp(current_path,directory)) {
        if (current_path) { espeak_Terminate(); free(current_path); current_path=NULL; }
        if (espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,0,directory,espeakINITIALIZE_DONT_EXIT) <= 0 ||
            espeak_SetVoiceByName("pl") != EE_OK) error(env,"Nie można uruchomić polskiej wymowy");
        else { current_path=directory;directory=NULL; }
    }
    pthread_mutex_unlock(&mutex);free(directory);
}
JNIEXPORT jbyteArray JNICALL Java_pl_ergenos_glosfacet_PolishPhonemizer_phonemes(JNIEnv *env,jobject self,jbyteArray input) {
    (void)self;char *text=utf8(env,input);if(!text)return NULL;
    pthread_mutex_lock(&mutex);
    jbyteArray result=NULL;
    if (!current_path) { error(env,"Polska wymowa nie jest gotowa");goto end; }
    const void *cursor=text;size_t used=0,capacity=32768;char *output=malloc(capacity);
    if(!output){error(env,"Za mało pamięci");goto end;}
    while(cursor) {
        const char *piece=espeak_TextToPhonemes(&cursor,espeakCHARS_UTF8,espeakPHONEMES_IPA);
        if(!piece)break;size_t length=strlen(piece);
        if(used+length+2>=capacity){error(env,"Wypowiedź jest za długa");free(output);goto end;}
        if(used)output[used++]=' ';memcpy(output+used,piece,length);used+=length;
    }
    result=(*env)->NewByteArray(env,(jsize)used);
    if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)used,(const jbyte*)output);
    free(output);
end:
    pthread_mutex_unlock(&mutex);free(text);return result;
}

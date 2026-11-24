#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>
#import <ImageIO/ImageIO.h>
#import <ScreenCaptureKit/ScreenCaptureKit.h>
#include <jni.h>

static NSData *jpeg(CGImageRef image) {
    if (!image) return nil;
    NSMutableData *data = [NSMutableData data];
    CGImageDestinationRef destination = CGImageDestinationCreateWithData(
            (__bridge CFMutableDataRef)data, CFSTR("public.jpeg"), 1, NULL);
    if (!destination) return nil;
    NSDictionary *options = @{ (__bridge NSString *)kCGImageDestinationLossyCompressionQuality: @0.75 };
    CGImageDestinationAddImage(destination, image, (__bridge CFDictionaryRef)options);
    BOOL written = CGImageDestinationFinalize(destination);
    CFRelease(destination);
    return written ? data : nil;
}

JNIEXPORT jboolean JNICALL Java_com_examsystem_client_proctor_MacDesktopCapture_isSupported(
        JNIEnv *env, jclass type) {
    if (@available(macOS 14.0, *)) return JNI_TRUE;
    return JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL Java_com_examsystem_client_proctor_MacDesktopCapture_captureJpeg(
        JNIEnv *env, jclass type) {
    @autoreleasepool {
        CGDirectDisplayID displayID = CGMainDisplayID();
        dispatch_semaphore_t finished = dispatch_semaphore_create(0);
        __block NSData *result = nil;
        void (^complete)(CGImageRef, NSError *) = ^(CGImageRef image, NSError *error) {
            if (!error) result = jpeg(image);
            dispatch_semaphore_signal(finished);
        };

        if (@available(macOS 15.2, *)) {
            [SCScreenshotManager captureImageInRect:CGDisplayBounds(displayID) completionHandler:complete];
        } else if (@available(macOS 14.0, *)) {
            [SCShareableContent getShareableContentExcludingDesktopWindows:NO onScreenWindowsOnly:YES
                    completionHandler:^(SCShareableContent *content, NSError *error) {
                for (SCDisplay *display in content.displays) {
                    if (display.displayID != displayID) continue;
                    SCContentFilter *filter = [[SCContentFilter alloc] initWithDisplay:display excludingWindows:@[]];
                    SCStreamConfiguration *configuration = [[SCStreamConfiguration alloc] init];
                    configuration.width = display.width;
                    configuration.height = display.height;
                    [SCScreenshotManager captureImageWithFilter:filter configuration:configuration
                            completionHandler:complete];
                    return;
                }
                complete(NULL, error);
            }];
        } else {
            return NULL;
        }

        if (dispatch_semaphore_wait(finished, dispatch_time(DISPATCH_TIME_NOW, 8 * NSEC_PER_SEC)) != 0
                || result.length == 0 || result.length > 5 * 1024 * 1024) {
            return NULL;
        }
        jbyteArray bytes = (*env)->NewByteArray(env, (jsize)result.length);
        if (bytes) (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)result.length, result.bytes);
        return bytes;
    }
}

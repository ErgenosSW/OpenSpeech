// SPDX-License-Identifier: GPL-3.0-only
import java.util.concurrent.atomic.AtomicBoolean;

/** Regression check for the exact callback method looked up by sherpa-onnx JNI. */
class CheckSynthesisCallback {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("pl.ergenos.glosfacet.MainActivity$SynthesisCallback");
        var method = type.getDeclaredMethod("invoke", float[].class);
        if (method.getReturnType() != Integer.class) {
            throw new AssertionError("JNI needs invoke(float[]): java.lang.Integer");
        }
        var constructor = type.getDeclaredConstructor(AtomicBoolean.class);
        constructor.setAccessible(true);
        method.setAccessible(true);
        var cancelled = new AtomicBoolean(false);
        Object callback = constructor.newInstance(cancelled);
        Object result = method.invoke(callback, (Object) new float[0]);
        if (!Integer.valueOf(1).equals(result)) throw new AssertionError("Must continue");
        cancelled.set(true);
        result = method.invoke(callback, (Object) new float[0]);
        if (!Integer.valueOf(0).equals(result)) throw new AssertionError("Must cancel");
        System.out.println("PASS: exact JNI callback signature, continuation and cancellation");
    }
}

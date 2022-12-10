/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class ThrowNode extends PNodeWithContext {

    private static final TruffleString T_CLOSE = tsLiteral("close");
    private static final TruffleString T_THROW = tsLiteral("throw");

    // Returns true when the generator finished
    public abstract boolean execute(VirtualFrame frame, int stackTop, Object iter, PException exception);

    @Specialization
    boolean doGenerator(VirtualFrame frame, int stackTop, PGenerator generator, PException exception,
                    @Cached GeneratorBuiltins.ThrowNode throwNode,
                    @Cached GeneratorBuiltins.CloseNode closeNode,
                    @Shared("exitProfile") @Cached IsBuiltinClassProfile profileExit,
                    @Shared("profile") @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        if (profileExit.profileException(exception, GeneratorExit)) {
            closeNode.execute(frame, generator);
            throw exception;
        } else {
            try {
                Object value = throwNode.execute(frame, generator, exception.getEscapedException(), PNone.NO_VALUE, PNone.NO_VALUE);
                frame.setObject(stackTop, value);
                return false;
            } catch (PException e) {
                handleException(frame, e, stopIterationProfile, getValue, stackTop);
                return true;
            }
        }
    }

    @Fallback
    boolean doOther(VirtualFrame frame, int stackTop, Object obj, PException exception,
                    @Cached PyObjectLookupAttr lookupThrow,
                    @Cached PyObjectLookupAttr lookupClose,
                    @Cached CallNode callThrow,
                    @Cached CallNode callClose,
                    @Cached WriteUnraisableNode writeUnraisableNode,
                    @Shared("exitProfile") @Cached IsBuiltinClassProfile profileExit,
                    @Shared("profile") @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        if (profileExit.profileException(exception, GeneratorExit)) {
            Object close = PNone.NO_VALUE;
            try {
                close = lookupClose.execute(frame, obj, T_CLOSE);
            } catch (PException e) {
                writeUnraisableNode.execute(frame, e.getEscapedException(), null, obj);
            }
            if (close != PNone.NO_VALUE) {
                callClose.execute(frame, close);
            }
            throw exception;
        } else {
            Object throwMethod = lookupThrow.execute(frame, obj, T_THROW);
            if (throwMethod == PNone.NO_VALUE) {
                throw exception;
            }
            try {
                Object value = callThrow.execute(frame, throwMethod);
                frame.setObject(stackTop, value);
                return false;
            } catch (PException e) {
                handleException(frame, e, stopIterationProfile, getValue, stackTop);
                return true;
            }
        }
    }

    private static void handleException(VirtualFrame frame, PException e, IsBuiltinClassProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue, int stackTop) {
        e.expectStopIteration(stopIterationProfile);
        Object value = getValue.execute(e.getUnreifiedException());
        frame.setObject(stackTop, null);
        frame.setObject(stackTop - 1, value);
    }

    public static ThrowNode create() {
        return ThrowNodeGen.create();
    }
}

/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import com.oracle.graal.python.util.PythonUtils;

public class PythonRawUnicodeEscapeCharsetEncoder extends CharsetEncoder {
    protected PythonRawUnicodeEscapeCharsetEncoder(Charset cs) {
        super(cs, 2, 10, new byte[]{(byte) '?'});
    }

    @Override
    protected CoderResult encodeLoop(CharBuffer source, ByteBuffer target) {
        while (true) {
            if (!source.hasRemaining()) {
                return CoderResult.UNDERFLOW;
            }
            if (!target.hasRemaining()) {
                return CoderResult.OVERFLOW;
            }
            int initialPosition = source.position();
            char ch = source.get();
            int codePoint = ch;
            if (Character.isHighSurrogate(ch)) {
                if (!source.hasRemaining()) {
                    source.position(initialPosition);
                    return CoderResult.UNDERFLOW;
                }
                char low = source.get();
                if (Character.isLowSurrogate(low)) {
                    codePoint = Character.toCodePoint(ch, low);
                } else {
                    // Unpaired surrogate - emit the high surrogate as is and process the low in the
                    // next iteration
                    source.position(source.position() - 1);
                }
            }
            if (codePoint <= 0xFF) {
                // ASCII
                target.put((byte) codePoint);
            } else {
                String hexString = PythonUtils.formatJString((codePoint <= 0xFFFF ? "\\u%04x" : "\\U%08x"), codePoint);
                for (int i = 0; i < hexString.length(); i++) {
                    if (!target.hasRemaining()) {
                        source.position(initialPosition);
                        target.position(target.position() - i);
                        return CoderResult.OVERFLOW;
                    }
                    target.put((byte) hexString.charAt(i));
                }
            }
        }
    }
}

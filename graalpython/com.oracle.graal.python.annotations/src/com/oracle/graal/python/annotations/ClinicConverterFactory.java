/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotates the factory method (which must be static) in the class specified by
 * {@link ArgumentClinic#conversionClass()}. There can be more such methods, but they must take
 * different number of arguments. The argument clinic code generator will choose the one suitable
 * given the provided extra arguments and other properties of the {@link ArgumentClinic} annotation.
 */
@Target(ElementType.METHOD)
public @interface ClinicConverterFactory {

    /**
     * The boxing optimized execute method variants will not attempt to cast the listed primitive
     * types and will just pass them directly to the specializations. This does not apply to
     * primitive values that are already boxed: those are always passed to the converter.
     */
    ArgumentClinic.PrimitiveType[] shortCircuitPrimitive() default {};

    /**
     * Annotates parameter of the factory method which will receive the default value
     * {@link ArgumentClinic#defaultValue()}.
     */
    @Target(ElementType.PARAMETER)
    @interface DefaultValue {
    }

    /**
     * Annotates parameter of the factory method which will receive the value of
     * {@link ArgumentClinic#useDefaultForNone()}.
     */
    @Target(ElementType.PARAMETER)
    @interface UseDefaultForNone {
    }

    /**
     * Annotates parameter of the factory method which will receive the name of the builtin
     * function.
     */
    @Target(ElementType.PARAMETER)
    @interface BuiltinName {
    }

    /**
     * Annotates parameter of the factory method which will receive the index of the argument of the
     * builtin functions.
     */
    @Target(ElementType.PARAMETER)
    @interface ArgumentIndex {
    }

    /**
     * Annotates parameter of the factory method which will receive the name of the argument of the
     * builtin functions.
     */
    @Target(ElementType.PARAMETER)
    @interface ArgumentName {
    }
}

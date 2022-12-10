/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.tck;

import static org.graalvm.polyglot.tck.TypeDescriptor.ANY;
import static org.graalvm.polyglot.tck.TypeDescriptor.BOOLEAN;
import static org.graalvm.polyglot.tck.TypeDescriptor.DATE;
import static org.graalvm.polyglot.tck.TypeDescriptor.DURATION;
import static org.graalvm.polyglot.tck.TypeDescriptor.EXCEPTION;
import static org.graalvm.polyglot.tck.TypeDescriptor.ITERABLE;
import static org.graalvm.polyglot.tck.TypeDescriptor.META_OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.NULL;
import static org.graalvm.polyglot.tck.TypeDescriptor.NUMBER;
import static org.graalvm.polyglot.tck.TypeDescriptor.OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.STRING;
import static org.graalvm.polyglot.tck.TypeDescriptor.TIME;
import static org.graalvm.polyglot.tck.TypeDescriptor.TIME_ZONE;
import static org.graalvm.polyglot.tck.TypeDescriptor.array;
import static org.graalvm.polyglot.tck.TypeDescriptor.executable;
import static org.graalvm.polyglot.tck.TypeDescriptor.hash;
import static org.graalvm.polyglot.tck.TypeDescriptor.instantiable;
import static org.graalvm.polyglot.tck.TypeDescriptor.intersection;
import static org.graalvm.polyglot.tck.TypeDescriptor.iterable;
import static org.graalvm.polyglot.tck.TypeDescriptor.iterator;
import static org.graalvm.polyglot.tck.TypeDescriptor.union;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public class PythonProvider implements LanguageProvider {

    private static final String ID = "python";

    private static final TypeDescriptor INT = NUMBER;
    // as per interop contract, we cannot be boolean and number at the same time, so it's only a
    // boolean
    private static final TypeDescriptor BOOL = BOOLEAN;
    private static final TypeDescriptor FLOAT = NUMBER;
    private static final TypeDescriptor COMPLEX = intersection(OBJECT);
    private static final TypeDescriptor NONE = intersection(NULL, OBJECT);
    private static final TypeDescriptor STR = intersection(OBJECT, STRING, ITERABLE, array(STRING));
    private static final TypeDescriptor BYTES = intersection(OBJECT, ITERABLE, array(INT));
    private static final TypeDescriptor BYTEARRAY = intersection(OBJECT, ITERABLE, array(INT));
    private static final TypeDescriptor DICT = dict(ANY, ANY);
    private static final TypeDescriptor SET = set(ANY);
    private static final TypeDescriptor LIST = list(ANY);
    private static final TypeDescriptor TUPLE = tuple(ANY);
    private static final TypeDescriptor DATETIME_DATE = intersection(OBJECT, DATE);
    private static final TypeDescriptor DATETIME_TIME = intersection(OBJECT, TIME);
    private static final TypeDescriptor DATETIME_DATETIME = intersection(OBJECT, DATE, TIME);
    private static final TypeDescriptor DATETIME_TIMEZONE = intersection(OBJECT, TIME_ZONE);
    private static final TypeDescriptor DATETIME_TIMEDELTA = intersection(OBJECT, DURATION);
    private static final TypeDescriptor BASE_EXCEPTION = intersection(OBJECT, EXCEPTION);

    private static final TypeDescriptor type(TypeDescriptor instance, boolean varargs, TypeDescriptor... params) {
        return intersection(OBJECT, instantiable(instance, varargs, params), executable(instance, varargs, params), META_OBJECT);
    }

    private static final TypeDescriptor list(TypeDescriptor componentType) {
        return intersection(OBJECT, iterable(componentType), array(componentType));
    }

    private static final TypeDescriptor tuple(TypeDescriptor componentType) {
        return intersection(OBJECT, iterable(componentType), array(componentType));
    }

    private static final TypeDescriptor dict(TypeDescriptor keyType, @SuppressWarnings("unused") TypeDescriptor valueType) {
        return intersection(OBJECT, iterable(keyType), hash(keyType, valueType));
    }

    private static final TypeDescriptor set(TypeDescriptor componentType) {
        return intersection(OBJECT, iterable(componentType));
    }

    private static final TypeDescriptor iter(TypeDescriptor componentType) {
        return intersection(OBJECT, iterable(componentType), iterator(componentType));
    }

    private static final TypeDescriptor lambda(TypeDescriptor returnType, TypeDescriptor... argTypes) {
        return intersection(OBJECT, executable(returnType, argTypes));
    }

    private static final TypeDescriptor function(TypeDescriptor returnType, TypeDescriptor... argTypes) {
        return intersection(OBJECT, executable(returnType, argTypes));
    }

    private static final TypeDescriptor generator(TypeDescriptor componentType) {
        return intersection(OBJECT, iterator(componentType), iterable(componentType));
    }

    public String getId() {
        return ID;
    }

    public Value createIdentityFunction(Context context) {
        return context.eval(ID, "lambda x: x");
    }

    private static void addValueSnippet(Context context, List<Snippet> snippets, String id, TypeDescriptor returnType, String code) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).build());
    }

    private static void addExpressionSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).parameterTypes(parameterTypes).build());
    }

    private static void addExpressionSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, ResultVerifier rv, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).resultVerifier(rv).parameterTypes(parameterTypes).build());
    }

    private static void addStatementSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).parameterTypes(parameterTypes).build());
    }

    public Collection<? extends Snippet> createValueConstructors(Context context) {
        // @formatter:off
        Object[] values = new Object[] {
            "BaseException", BASE_EXCEPTION, "BaseException()",
            "NoneType", NONE, "None",
            "bool:True", BOOL, "True",
            "bool:False", BOOL, "False",
            "int", INT, "1",
            "float", FLOAT, "1.1",
            "complex", COMPLEX, "1.0j",
            "str", STR, "class pstr(str):\n pass\npstr('hello world')",
            "bytes", BYTES, "b'1234'",
            "bytearray", BYTEARRAY, "bytearray([1,4,2])",
            "list", LIST, "[1, object(), 'q']",
            "list:int", list(INT), "[1,2,3]",
            "list:str", list(STR), "['a', 'b', 'c']",
            "tuple", TUPLE, "(1, object(), 'q')",
            "tuple:int", tuple(INT), "(1,2,3)",
            "tuple:str", tuple(STR), "('a', 'b', 'c')",
            "dict", DICT, "{object(): 'q'}",
            "dict:int-str", dict(INT, STR), "{1: 'q'}",
            "dict:str-int", dict(STR, INT), "{'q': 1}",
            "set", SET, "{object(), 'q', 12}",
            "datetime", DATETIME_DATETIME, "import datetime; datetime.datetime.now()",
            "date", DATETIME_DATE, "import datetime; datetime.date.today()",
            "time", DATETIME_TIME, "import datetime; datetime.datetime.now().time()",
            "timedelta", DATETIME_TIMEDELTA, "import datetime; datetime.timedelta(hours=2)",
            "timezone", DATETIME_TIMEZONE, "import datetime; datetime.timezone(datetime.timedelta(hours=2))",
            "type:builtin", type(OBJECT, false), "object",
            "type:user", type(OBJECT, false), "class type_user():\n    pass\ntype_user",
            // TODO remove '*args' from following value constructors once this is fixed in Truffle TCK
            "lambda:id", lambda(ANY, ANY), "lambda x, *args: x",
            "lambda:+1", lambda(NUMBER, NUMBER), "lambda x, *args: x + 1",
            "iter", iter(ANY), "iter([1, 'q', object()])",
            "iter:int", iter(INT), "iter([1, 2, 3])",
            "function:id", function(ANY, ANY), "def function_id(x, *args):\n    return x\nfunction_id",
            "function:+1", function(NUMBER, NUMBER), "def function_add1(x, *args):\n    return x + 1\nfunction_add1",
            "generator:any", generator(ANY), "def generator_any():\n    yield object()\ngenerator_any()",
            "generator:number", generator(NUMBER), "def generator_number():\n    yield 12\ngenerator_number()"
        };
        // @formatter:on

        List<Snippet> snippets = new ArrayList<>();
        for (int i = 0; i < values.length; i += 3) {
            String snippet = (String) values[i + 2];
            String delim = ";";
            if (snippet.contains("\n")) {
                delim = "\n";
            }
            String[] parts = snippet.split(delim);
            parts[parts.length - 1] = "lambda: " + parts[parts.length - 1];
            snippet = String.join(delim, parts);
            addValueSnippet(context, snippets, (String) values[i], (TypeDescriptor) values[i + 1], snippet);
        }
        return snippets;
    }

    public Collection<? extends Snippet> createExpressions(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        // @formatter:off
        addExpressionSnippet(context, snippets, "+", "lambda x, y: x + y", union(STRING, NUMBER, array(ANY)), AddVerifier.INSTANCE, union(STRING, BOOLEAN, NUMBER, array(ANY)), union(STRING, BOOLEAN, NUMBER, array(ANY)));
        addExpressionSnippet(context, snippets, "*", "lambda x, y: x * y", union(STRING, NUMBER, array(ANY)), MulVerifier.INSTANCE, union(STRING, BOOLEAN, NUMBER, array(ANY)), union(STRING, BOOLEAN, NUMBER, array(ANY)));

        addExpressionSnippet(context, snippets, "-", "lambda x, y: x - y", NUMBER, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));

        addExpressionSnippet(context, snippets, "/", "lambda x, y: x / y", NUMBER, PDivByZeroVerifier.INSTANCE, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));

        addExpressionSnippet(context, snippets, "list-from-foreign", "lambda x: list(x)", array(ANY), union(STRING, iterable(ANY), iterator(ANY), array(ANY), hash(ANY, ANY)));

        addExpressionSnippet(context, snippets, "==", "lambda x, y: x == y", BOOLEAN, ANY, ANY);
        addExpressionSnippet(context, snippets, "!=", "lambda x, y: x != y", BOOLEAN, ANY, ANY);
        addExpressionSnippet(context, snippets, ">", "lambda x, y: x > y", BOOLEAN, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));
        addExpressionSnippet(context, snippets, ">=", "lambda x, y: x >= y", BOOLEAN, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));
        addExpressionSnippet(context, snippets, "<", "lambda x, y: x < y", BOOLEAN, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));
        addExpressionSnippet(context, snippets, "<=", "lambda x, y: x <= y", BOOLEAN, union(BOOLEAN, NUMBER), union(BOOLEAN, NUMBER));

        addExpressionSnippet(context, snippets, "isinstance", "lambda x, y: isinstance(x, y)", BOOLEAN, ANY, META_OBJECT);
        addExpressionSnippet(context, snippets, "issubclass", "lambda x, y: issubclass(x, y)", BOOLEAN, META_OBJECT, META_OBJECT);

        addExpressionSnippet(context, snippets, "[]", "lambda x, y: x[y]", ANY, GetItemVerifier.INSTANCE, union(array(ANY), STRING, hash(ANY, ANY)), ANY);
        addExpressionSnippet(context, snippets, "[a:b]", "lambda x: x[:]", union(STRING, array(ANY)), union(STRING, array(ANY)));

        // @formatter:on
        return snippets;
    }

    public Collection<? extends Snippet> createStatements(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        // @formatter:off

        addStatementSnippet(context, snippets, "assert", "def gen_assert(v):\n" +
                                                         "    assert v or not v\n\n" +
                                                         "gen_assert", NULL, ANY);

        addStatementSnippet(context, snippets, "return", "def gen_return(x):\n" +
                                                         "    return x\n\n" +
                                                         "gen_return", ANY, ANY);

        addStatementSnippet(context, snippets, "if", "def gen_if(p):\n" +
                                                     "   if p:\n" +
                                                     "      return True\n" +
                                                     "   else:\n" +
                                                     "      return False\n\n" +
                                                     "gen_if", BOOLEAN, ANY);

        addStatementSnippet(context, snippets, "for", "def gen_for(l):\n" +
                                                      "    for x in l:\n" +
                                                      "        return x\n\n" +
                                                      "gen_for", ANY, union(array(ANY), iterable(ANY), iterator(ANY), STRING, hash(ANY, ANY)));

        // any exception honours the finally block, but non-exception cannot be raised
        addStatementSnippet(context, snippets, "try-finally", "def gen_tryfinally(exc):\n" +
                                                      "    cannot_raise = None\n" +
                                                      "    try:\n" +
                                                    "          raise exc\n" +
                                                      "    except TypeError as e:\n" +
                                                      "        cannot_raise = e\n" +
                                                      "    finally:\n" +
                                                      "        if cannot_raise:\n" +
                                                      "            raise cannot_raise\n" +
                                                      "        else:\n" +
                                                      "            return True\n" +
                                                      "gen_tryfinally", BOOLEAN, EXCEPTION);

        // any exception can be caught, but non-exceptions cannot be raised
        addStatementSnippet(context, snippets, "try-except", "def gen_tryexcept(exc):\n" +
                                                      "    try:\n" +
                                                      "        raise exc\n" +
                                                      "    except TypeError as e:\n" +
                                                      "        raise e\n" +
                                                      "    except:\n" +
                                                      "        return True\n" +
                                                      "gen_tryexcept", BOOLEAN, EXCEPTION);

        // @formatter:on

        return snippets;
    }

    public Collection<? extends Snippet> createScripts(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        snippets.add(loadScript(context, "resources/sieve.py", array(NUMBER)));
        snippets.add(loadScript(context, "resources/euler31.py", NUMBER));
        snippets.add(loadScript(context, "resources/mandelbrot3.py", NULL));

        return snippets;
    }

    public Collection<? extends Source> createInvalidSyntaxScripts(Context context) {
        try {
            return Arrays.asList(createSource("resources/invalid_syntax0.py"),
                            createSource("resources/invalid_syntax1.py"));
        } catch (IOException e) {
            throw new AssertionError("IOException while creating a test script.", e);
        }
    }

    private static Snippet loadScript(
                    Context context,
                    String resourceName,
                    TypeDescriptor resultType) {
        try {
            Source src = createSource(resourceName);
            return Snippet.newBuilder(src.getName(), context.eval(src), resultType).build();
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script.", ioe);
        }
    }

    private static Source createSource(String resourceName) throws IOException {
        int slashIndex = resourceName.lastIndexOf('/');
        String scriptName = slashIndex >= 0 ? resourceName.substring(slashIndex + 1) : resourceName;
        Reader in = new InputStreamReader(PythonProvider.class.getResourceAsStream(resourceName), "UTF-8");
        try {
            return Source.newBuilder(ID, in, scriptName).build();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private abstract static class PResultVerifier implements ResultVerifier {
    }

    /**
     * Only accepts exact matches of types.
     */
    private static class AddVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            // If both parameter values are lists, then ignore if they have different types. E.g.
            // ignore '(1,2) + [3,4]'.
            if (par0.hasArrayElements() && par1.hasArrayElements()) {
                if (par0.getMetaObject() == par1.getMetaObject()) {
                    assert snippetRun.getException() == null;
                    TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                    assert array(ANY).isAssignable(resultType);
                }
            } else if (par0.isString() && par1.isString()) {
                assert snippetRun.getException() == null;
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                assert STRING.isAssignable(resultType);
            } else if ((par0.isNumber() || par0.isBoolean()) && (par1.isNumber() || par1.isBoolean())) {
                assert snippetRun.getException() == null;
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                assert NUMBER.isAssignable(resultType);
            } else {
                assert snippetRun.getException() != null;
                TypeDescriptor argType = union(STRING, BOOLEAN, NUMBER, array(ANY));
                TypeDescriptor par0Type = TypeDescriptor.forValue(par0);
                TypeDescriptor par1Type = TypeDescriptor.forValue(par1);
                if (!argType.isAssignable(par0Type) || !argType.isAssignable(par1Type)) {
                    // argument type error, rethrow
                    throw snippetRun.getException();
                } else {
                    // arguments are ok, just don't work in this combination
                }
            }
        }

        private static final AddVerifier INSTANCE = new AddVerifier();
    }

    private static class MulVerifier extends PResultVerifier {

        private static boolean isStringMul(Value x, Value y) {
            return x.isString() && (y.isBoolean() || (y.isNumber() && y.fitsInInt()));
        }

        private static boolean isArrayMul(Value x, Value y) {
            return x.hasArrayElements() && (y.isBoolean() || (y.isNumber() && y.fitsInInt()));
        }

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            if (isStringMul(par0, par1) || isStringMul(par1, par0)) {
                // string * number => string
                assert snippetRun.getException() == null;
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                assert STRING.isAssignable(resultType);
            } else if ((par0.isNumber() || par0.isBoolean()) && (par1.isNumber() || par1.isBoolean())) {
                // number * number has greater precendence than array * number
                assert snippetRun.getException() == null;
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                assert NUMBER.isAssignable(resultType) : resultType.toString();
            } else if (isArrayMul(par0, par1) || isArrayMul(par1, par0)) {
                // array * number => array
                assert snippetRun.getException() == null;
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                assert array(ANY).isAssignable(resultType);
            } else {
                assert snippetRun.getException() != null;
                TypeDescriptor argType = union(STRING, BOOLEAN, NUMBER, array(ANY));
                TypeDescriptor par0Type = TypeDescriptor.forValue(par0);
                TypeDescriptor par1Type = TypeDescriptor.forValue(par1);
                if (!argType.isAssignable(par0Type) || !argType.isAssignable(par1Type)) {
                    // argument type error, rethrow
                    throw snippetRun.getException();
                } else {
                    // arguments are ok, just don't work in this combination
                }
            }
        }

        private static final MulVerifier INSTANCE = new MulVerifier();
    }

    private static class GetItemVerifier extends PResultVerifier {
        private static final String[] UNHASHABLE_TYPES = new String[]{"list", "dict", "bytearray", "set"};

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            long len = -1;

            if (par0.hasArrayElements()) {
                len = par0.getArraySize();
            } else if (par0.isString()) {
                len = par0.asString().length();
            }
            if (len >= 0) {
                int idx;
                if (par1.isBoolean()) {
                    idx = par1.asBoolean() ? 1 : 0;
                } else if (par1.isNumber() && par1.fitsInInt()) {
                    idx = par1.asInt();
                } else {
                    assert snippetRun.getException() != null;
                    return;
                }
                if ((idx >= 0 && len > idx) || (idx < 0 && idx + len >= 0 && len > idx + len)) {
                    assert snippetRun.getException() == null : snippetRun.getException().toString();
                } else {
                    assert snippetRun.getException() != null;
                }
            } else if (par0.hasHashEntries()) {
                if (par1.getMetaObject() != null) {
                    String metaName = par1.getMetaObject().getMetaQualifiedName();
                    for (String s : UNHASHABLE_TYPES) {
                        if (metaName.equals(s)) {
                            // those don't work, but that's expected
                            assert snippetRun.getException() != null;
                            return;
                        }
                    }
                }
                Value v = par0.getHashValueOrDefault(par1, PythonProvider.class.getName());
                if (v.isString() && v.asString().equals(PythonProvider.class.getName())) {
                    assert snippetRun.getException() != null;
                } else {
                    assert snippetRun.getException() == null : snippetRun.getException().toString();
                }
            } else {
                // argument type error, rethrow
                throw snippetRun.getException();
            }
        }

        private static final GetItemVerifier INSTANCE = new GetItemVerifier();
    }

    /**
     * Only accepts exact matches of types.
     */
    private static class PDivByZeroVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            // If anumber/Boolean should be divided, ignore if divisor is Boolean false
            if (!par0.isNumber() && !par0.isBoolean() || !par1.isBoolean() || par1.asBoolean()) {
                ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
            }
        }

        private static final PDivByZeroVerifier INSTANCE = new PDivByZeroVerifier();
    }
}

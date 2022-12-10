# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import os
from unittest import skipIf

import sys

if sys.implementation.name == "graalpy":
    import polyglot
    from __graalpython__ import is_native

    def test_import():
        def some_function():
            return "hello, polyglot world!"
        polyglot.export_value(some_function)
        imported_fun0 = polyglot.import_value("some_function")
        assert imported_fun0 is some_function
        assert imported_fun0() == "hello, polyglot world!"

        polyglot.export_value("same_function", some_function)
        imported_fun1 = polyglot.import_value("same_function")
        assert imported_fun1 is some_function
        assert imported_fun1() == "hello, polyglot world!"

    class GetterOnly():
        def __get__(self, instance, owner):
            pass

    class CustomObject():
        field = 42

        def __getitem__(self, item):
            return item * 2

        def __len__(self):
            return 21

        getter = GetterOnly()

        @property
        def setter(self):
            pass

        @setter.setter
        def setter_setter(self):
            pass

    class CustomMutable(CustomObject):
        _items = {}

        def keys(self):
            return self._items.keys()

        def items(self):
            return self._items.items()

        def values(self):
            return self._items.values()

        def __getitem__(self, key):
            return self._items[key]

        def __setitem__(self, key, item):
            self._items[key] = item

        def __delitem__(self, key):
            del self._items[key]

    class PyString(str):
        pass

    def test_read():
        o = CustomObject()
        assert polyglot.__read__(o, "field") == o.field
        assert polyglot.__read__(o, 10) == o[10]

    def test_write():
        o = CustomMutable()
        o2 = CustomObject()

        polyglot.__write__(o, "field", 32)
        assert o.field == 32

        polyglot.__write__(o, "__getattribute__", 321)
        assert o.__getattribute__ == 321

        polyglot.__write__(o, "grrrr", 42)
        assert hasattr(o, "grrrr")
        polyglot.__write__(o2, "grrrr", 42)
        assert o2.grrrr == 42

        try:
            non_string = bytearray(b"a fine non-string object we have here")
            polyglot.__write__(o, non_string, 12)
        except AttributeError:
            assert True
        else:
            assert False

    def test_remove():
        o = CustomMutable()
        o.direct_field = 111

        polyglot.__remove__(o, "direct_field")
        assert not hasattr(o, "direct_field")

        o.direct_field = 12
        o["direct_field"] = 32
        assert "direct_field" in list(o.keys())


    def test_execute():
        assert polyglot.__execute__(abs, -10) == 10
        o = CustomMutable()
        assert polyglot.__execute__(o.__getattribute__, "field") == o.field

    def test_invoke():
        o = CustomMutable()
        assert polyglot.__invoke__(o, "__getattribute__", "field") == o.field

    def test_new():
        assert isinstance(polyglot.__new__(CustomMutable), CustomMutable)

    def test_is_null():
        assert polyglot.__is_null__(None)

    def test_has_size():
        import array

        assert polyglot.__has_size__((0,))
        assert polyglot.__has_size__([])
        assert polyglot.__has_size__(array.array('b'))
        assert polyglot.__has_size__(bytearray(b""))
        assert polyglot.__has_size__(b"")
        assert polyglot.__has_size__(PyString(""))
        assert polyglot.__has_size__(range(10))
        assert polyglot.__has_size__(CustomObject())

        assert not polyglot.__has_size__(object())

    def test_get_size():
        called = False

        class LenObject():

            def __getitem__(self, k):
                if k == 0:
                    return 1
                else:
                    raise IndexError

            def __len__(self):
                nonlocal called
                called = True
                return 1

        assert polyglot.__get_size__(LenObject()) == 1
        assert called

    def test_has_keys():
        assert not polyglot.__has_keys__(True)
        assert polyglot.__has_keys__(None)
        assert polyglot.__has_keys__(NotImplemented)
        assert not polyglot.__has_keys__(False)
        assert polyglot.__has_keys__(object())

    def test_keys():
        o = CustomObject()
        inherited_keys = len(polyglot.__keys__(o))
        o.my_field = 1
        assert len(polyglot.__keys__(o)) == 1 + inherited_keys
        assert "my_field" in polyglot.__keys__(o)

    def test_key_info():
        o = CustomObject()
        o.my_field = 1
        o.test_exec = lambda: False

        assert polyglot.__key_info__(o, "__len__", "readable")
        assert polyglot.__key_info__(o, "__len__", "invokable")
        assert polyglot.__key_info__(o, "__len__", "modifiable")
        assert polyglot.__key_info__(o, "__len__", "removable")
        assert not polyglot.__key_info__(o, "__len__", "insertable")

        assert polyglot.__key_info__(o, "test_exec", "readable")
        assert polyglot.__key_info__(o, "test_exec", "invokable")
        assert polyglot.__key_info__(o, "test_exec", "modifiable")
        assert polyglot.__key_info__(o, "test_exec", "removable")
        assert not polyglot.__key_info__(o, "test_exec", "insertable")

        assert polyglot.__key_info__(o, "my_field", "readable")
        assert not polyglot.__key_info__(o, "my_field", "invokable")
        assert polyglot.__key_info__(o, "my_field", "modifiable")
        assert polyglot.__key_info__(o, "my_field", "removable")
        assert not polyglot.__key_info__(o, "my_field", "insertable")

        assert polyglot.__key_info__(o, "__getattribute__", "readable")
        assert polyglot.__key_info__(o, "__getattribute__", "invokable")
        assert not polyglot.__key_info__(o, "__getattribute__", "removable")
        assert not polyglot.__key_info__(o, "__getattribute__", "insertable")

        builtinObj = (1,2,3)
        assert polyglot.__key_info__(builtinObj, "__len__", "readable")
        assert polyglot.__key_info__(builtinObj, "__len__", "invokable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "modifiable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "removable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "insertable")

    @skipIf(is_native, "not supported in native mode")
    def test_java_classpath():
        import java
        try:
            java.add_to_classpath(1)
        except TypeError as e:
            assert "classpath argument 1 must be string, not int" in str(e)

        try:
            java.add_to_classpath('a', 1)
        except TypeError as e:
            assert "classpath argument 2 must be string, not int" in str(e)

    @skipIf(is_native, "not supported in native mode")
    def test_host_lookup():
        import java
        try:
            strClass = java.type("java.lang.String")
            assert strClass.valueOf(True) == "true"
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)

        try:
            java.type("this.type.does.not.exist")
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)
        except KeyError as e:
            assert True
        else:
            assert False, "requesting a non-existing host symbol should raise KeyError"

    def test_internal_languages_dont_eval():
        try:
            polyglot.eval(language="nfi", string="default")
        except NotImplementedError as e:
            assert "No language for id nfi found" in str(e)

        assert polyglot.eval(language="python", string="21 * 2") == 42

    @skipIf(is_native, "not supported in native mode")
    def test_non_index_array_access():
        import java
        try:
            al = java.type("java.util.ArrayList")()
            assert al.size() == len(al) == 0
        except IndexError:
            assert False, "using __getitem__ to access keys of an array-like foreign object should work"
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)

    @skipIf(is_native, "not supported in native mode")
    def test_direct_call_of_truffle_object_methods():
        import java
        try:
            al = java.type("java.util.ArrayList")()
            assert al.__len__() == al.size() == len(al)
        except IndexError:
            assert False, "calling the python equivalents for well-known functions directly should work"
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)

    def test_array_element_info():
        immutableObj = (1,2,3,4)
        assert polyglot.__element_info__(immutableObj, 0, "exists")
        assert polyglot.__element_info__(immutableObj, 0, "readable")
        assert not polyglot.__element_info__(immutableObj, 0, "removable")
        assert not polyglot.__element_info__(immutableObj, 0, "writable")
        assert not polyglot.__element_info__(immutableObj, 0, "insertable")
        assert not polyglot.__element_info__(immutableObj, 0, "modifiable")
        assert not polyglot.__element_info__(immutableObj, 4, "insertable")

        mutableObj = [1,2,3,4]
        assert polyglot.__element_info__(mutableObj, 0, "exists")
        assert polyglot.__element_info__(mutableObj, 0, "readable")
        assert polyglot.__element_info__(mutableObj, 0, "removable")
        assert polyglot.__element_info__(mutableObj, 0, "writable")
        assert not polyglot.__element_info__(mutableObj, 0, "insertable")
        assert polyglot.__element_info__(mutableObj, 0, "modifiable")
        assert polyglot.__element_info__(mutableObj, 4, "insertable")

    @skipIf(is_native, "not supported in native mode")
    def test_java_imports():
        import java
        try:
            al = java.type("java.util.ArrayList")()
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)
        else:
            import java.util.ArrayList
            assert repr(java.util.ArrayList()) == "[]"

            from java.util import ArrayList
            assert repr(ArrayList()) == "[]"

            if __graalpython__.jython_emulation_enabled:
                assert java.util.ArrayList == ArrayList

                import sun
                assert type(sun.misc) is type(java)

                import sun.misc.Signal
                assert sun.misc.Signal is not None

    def test_java_import_from_jar():
        if __graalpython__.jython_emulation_enabled:
            import tempfile
            import zipfile

            # import a single file with jar!prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_import.py", mode="w") as member:
                    member.write(b"MEMBER = 42\n")
            try:
                sys.path.append(tempname + "!scriptDir")
                try:
                    import test_java_jar_import
                    assert test_java_jar_import.MEMBER == 42
                    assert test_java_jar_import.__path__ == tempname + "/scriptDir/test_java_jar_import.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)

            # import a single file with jar!/prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_import_2.py", mode="w") as member:
                    member.write(b"MEMBER = 43\n")
            try:
                sys.path.append(tempname + "!/scriptDir")
                try:
                    import test_java_jar_import_2
                    assert test_java_jar_import_2.MEMBER == 43
                    assert test_java_jar_import_2.__path__ == tempname + "/scriptDir/test_java_jar_import_2.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)

            # import a package with jar!/prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_pkg/__init__.py", mode="w") as member:
                    member.write(b"MEMBER = 44\n")
            try:
                sys.path.append(tempname + "!/scriptDir")
                try:
                    import test_java_jar_pkg
                    assert test_java_jar_pkg.MEMBER == 44
                    assert test_java_jar_pkg.__path__ == tempname + "/scriptDir/test_java_jar_pkg/__init__.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)


    def test_java_exceptions():
        if __graalpython__.jython_emulation_enabled:
            from java.lang import Integer, NumberFormatException
            try:
                Integer.parseInt("99", 8)
            except NumberFormatException as e:
                assert True
            else:
                assert False

    @skipIf(is_native, "not supported in native mode")
    def test_foreign_object_does_not_leak_Javas_toString():
        try:
            from java.util import ArrayList
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)
        else:
            try:
                ArrayList(12, "12")
            except TypeError as e:
                assert "@" not in str(e) # the @ from Java's default toString

            try:
                ArrayList(12, foo="12") # keywords are not supported
            except TypeError as e:
                assert "@" not in str(e) # the @ from Java's default toString

            try:
                ArrayList.bar
            except AttributeError as e:
                assert "@" not in str(e) # the @ from Java's default toString

            try:
                del ArrayList.bar
            except AttributeError as e:
                assert "@" not in str(e) # the @ from Java's default toString

            try:
                del ArrayList.bar
            except AttributeError as e:
                assert "@" not in str(e) # the @ from Java's default toString

    def test_java_import_star():
        if __graalpython__.jython_emulation_enabled:
            d = {}
            exec("from java.util.logging.Logger import *", globals=d, locals=d)
            assert "getGlobal" in d
            assert d["getGlobal"]().getName() == d["GLOBAL_LOGGER_NAME"]

    @skipIf(is_native, "not supported in native mode")
    def test_java_null_is_none():
        import java.lang.Integer as Integer
        x = Integer.getInteger("something_what_does_not_exists")
        y = Integer.getInteger("something_what_does_not_exists2")
        z = None

        if __graalpython__.jython_emulation_enabled:

            assert x == None
            assert (x != None) == False
            assert x is None
            assert (x is not None) == False

            assert x == y
            assert (x != y) == False
            assert x is y
            assert (x is not y) == False

            assert x == z
            assert (x != z) == False
            assert x is z
            assert (x is not z) == False

        else:

            assert x == None
            assert (x != None) == False
            assert (x is None) == False
            assert x is not None

            assert x == y
            assert (x != y) == False
            assert x is y
            assert (x is not y) == False

            assert x == z
            assert (x != z) == False
            assert (x is z) == False
            assert x is not z

    def test_isinstance01():
        if __graalpython__.jython_emulation_enabled:
            import java.lang.Integer as Integer
            i = Integer(1)
            assert isinstance(i, Integer)

    def test_isinstance02():
        if __graalpython__.jython_emulation_enabled:
            import java.util.Map as Map
            import java.util.HashMap as HashMap
            h = HashMap()
            assert isinstance(h, HashMap)
            assert isinstance(h, Map)

    @skipIf(is_native, "not supported in native mode")
    def test_is_type():
        import java
        from java.util.logging import Handler
        from java.util import Set
        from java.util.logging import LogRecord
        from java.util.logging import Level

        assert java.is_type(Handler)
        assert java.is_type(LogRecord)
        assert java.is_type(Set)
        assert java.is_type(Level)

        lr = LogRecord(Level.ALL, "message")
        assert not java.is_type(lr)
        assert not java.is_type(Level.ALL)
        assert not java.is_type("ahoj")

    @skipIf(is_native, "not supported in native mode")
    def test_extend_java_class_01():
        from java.util.logging import Handler
        from java.util.logging import LogRecord
        from java.util.logging import Level

        lr = LogRecord(Level.ALL, "The first message")

        # extender object
        class MyHandler (Handler):
            "This is MyHandler doc"
            counter = 0;
            def isLoggable(self, logrecord):
                self.counter = self.counter + 1
                return self.__super__.isLoggable(logrecord)
            def sayHello(self):
                return 'Hello'

        h = MyHandler()

        # accessing extender object via this property
        assert hasattr(h, 'this')
        assert hasattr(h.this, 'sayHello')
        assert hasattr(h.this, 'counter')
        assert hasattr(h.this, 'isLoggable')

        #accessing java methods or methods from extender object directly
        assert hasattr(h, 'close')
        assert hasattr(h, 'flush')
        assert hasattr(h, 'getEncoding')
        assert hasattr(h, 'setEncoding')


        assert h.this.counter == 0
        assert h.isLoggable(lr)
        assert h.this.counter == 1
        assert h.isLoggable(lr)
        assert h.isLoggable(lr)
        assert h.this.counter == 3

        assert 'Hello' == h.this.sayHello()

        h2 = MyHandler()
        assert h2.this.counter == 0
        assert h2.isLoggable(lr)
        assert h2.this.counter == 1
        assert h.this.counter == 3

    @skipIf(is_native, "not supported in native mode")
    def test_extend_java_class_02():
        from java.math import BigDecimal
        try:
            class MyDecimal(BigDecimal):
                pass
        except TypeError:
            assert True
        else:
            assert False

    @skipIf(is_native, "not supported in native mode")
    def test_extend_java_class_03():
        #test of java constructor
        from java.util.logging import LogRecord
        from java.util.logging import Level

        class MyLogRecord(LogRecord):
            def getLevel(self):
                if self.__super__.getLevel() == Level.FINEST:
                    self.__super__.setLevel(Level.WARNING)
                return self.__super__.getLevel()

        message = "log message"
        my_lr1 = MyLogRecord(Level.WARNING, message)
        assert my_lr1.getLevel() == Level.WARNING
        assert my_lr1.getMessage() == message

        my_lr2 = MyLogRecord(Level.FINEST, message)
        assert my_lr2.getLevel() == Level.WARNING

    def test_foreign_slice_setting():
        import java
        il = java.type("int[]")(20)
        try:
            il[0:2] = 1
        except TypeError:
            assert True
        else:
            assert False, "should throw a type error"
        il[0] = 12
        assert il[0] == 12
        il[0:10] = [10] * 10
        assert list(il) == [10] * 10 + [0] * 10, "not equal"
        try:
            il[0] = 1.2
        except TypeError:
            assert True
        else:
            assert False, "should throw a type error again"

    @skipIf(is_native, "not supported in native mode")
    def test_foreign_repl():
        from java.util.logging import LogRecord
        from java.util.logging import Level

        lr = LogRecord(Level.ALL, "message")
        assert repr(LogRecord).startswith('<JavaClass[java.util.logging.LogRecord] at')
        assert repr(lr).startswith('<JavaObject[java.util.logging.LogRecord] at')

        from java.lang import Integer
        i = Integer('22')
        assert repr(Integer).startswith('<JavaClass[java.lang.Integer] at')
        assert repr(i) == '22'

    def test_jython_star_import():
        if __graalpython__.jython_emulation_enabled:
            g = {}
            exec('from java.lang.Byte import *', g)
            assert type(g['MAX_VALUE']) is int

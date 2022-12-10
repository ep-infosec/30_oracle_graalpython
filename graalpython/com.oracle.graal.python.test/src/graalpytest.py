# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

#!/usr/bin/env mx python
import os
from os.path import dirname, join
import os.path as ospath
import _io
import sys
import time
import _thread
import collections
import tempfile


# A list of tests that cannot run in parallel with other tests
SERIAL_TESTS = [
    # test_compileall tries to recompile the whole PYTHONPATH, which makes it interfere with any test that
    # creates temporary py files
    'test_compileall',
    # test_import tests various behaviors related to __pycache__ directory,
    # it can interfere with other tests that generate code
    'test_import',
    'test_subprocess',
    'test_posix',
    'test_io',
    'test_fileio',
    'test_imaplib',
    "test_ftplib",
    'test_multiprocessing_spawn',
    # trying to avoid transient issues there, not sure about the reason
    'test_unittest',
]


os = sys.modules.get("posix", sys.modules.get("nt", None))
if os is None:
    raise ImportError("posix or nt module is required in builtin modules")

FAIL = '\033[91m'
ENDC = '\033[0m'
BOLD = '\033[1m'

class SkipTest(BaseException):
    pass

_fixture_scopes = {"session": {}, "module": {}, "function": {}}
_fixture_marks = []
_itertools_module = None
_request_class = collections.namedtuple("request", "param config")
_loaded_conftest = {}
_created_tmp_dirs = []

class RequestConfig:
    def getoption(self, name):
        return False


class Fixture:
    def __init__(self, fun, **kwargs):
        self.fun = fun
        co = fun.__code__
        self.requests_context = "request" in co.co_varnames[:co.co_argcount]
        self.params = kwargs.get("params", [])
        self._values = None

    def eval(self, test_class_instance):
        values = []
        if self.params:
            for param in self.params:
                values.extend(self._call_fixture_func(test_class_instance, _request_class(param=param, config=RequestConfig())))
        else:
            values.extend(self._call_fixture_func(test_class_instance, _request_class(param=None, config=RequestConfig())))
        self._values = values
        return values

    def _call_fixture_func(self, test_class_instance, request):
        co = self.fun.__code__
        fixture_args = []
        start = 1 if self.requests_context else 0
        has_self = False
        if co.co_argcount > start:
            arg_names = co.co_varnames[start:co.co_argcount]
            has_self = arg_names and arg_names[0] == "self"
            if has_self:
                arg_names = arg_names[1:]
            fixture_args = get_fixture_values(test_class_instance, arg_names)
        results = []
        fixed_args = ((test_class_instance, ) if has_self else tuple()) + ((request, ) if self.requests_context else tuple())
        if fixture_args:
            for arg_vec in fixture_args:
                args = fixed_args + arg_vec
                results.append(self.fun(*args))
        else:
            results.append(self.fun(*fixed_args))
        return results

    def values(self):
        return self._values

    def name(self):
        return self.fun.__name__


class GraalPyTempdir:
    def __init__(self, path):
        assert isinstance(path, str)
        self._path = path

    def __fspath__(self):
        return self._path

    def __str__(self):
        return self._path

    def join(self, *args):
        res = ospath.join(self._path, *args)
        return GraalPyTempdir(res)

    def write(self, data, mode='w', ensure=False):
        with open(self._path, mode) as f:
            f.write(data)


def eval_fixture(name, test_class_instance):
    """
    Evaluate a fixtures with default scope (=function).
    """
    # TODO remove special handling
    if name == "tmpdir":
        tmp_dir = tempfile.mkdtemp()
        global _created_tmp_dirs
        _created_tmp_dirs.append(tmp_dir)
        return [GraalPyTempdir(tmp_dir)]

    fixture = _fixture_scopes["function"].get(name)
    if fixture:
        assert name == fixture.name()
        return fixture.eval(test_class_instance)
    raise ValueError("unknown fixture " + name)


def eval_scope(scope):
    """
    Evaluate fixtures in the given scope.
    """
    for name, f in _fixture_scopes[scope].items():
        assert name == f.name()
        f.eval(None)


def is_existing_fixture(name):
    """
    Search in all scopes if there is a fixture with the given name.
    """
    for scope in _fixture_scopes.values():
        if scope.get(name):
            return True
    return False


def get_fixture_values(test_class_instance, names):
    """
    Computes a list of argument vectors for the given fixture names.
    It's a list because of parameterized fixtures that cause a cartesian product of all fixture values.
    """
    result_vector = []
    for fixture_name in names:
        for scope_name, scope in _fixture_scopes.items():
            # fixtures in higher scope are already eval'd
            if scope_name == "session" or scope_name == "module":
                fixture = scope.get(fixture_name)
                if fixture:
                    # evaluate fixtures on demand (could be that we loaded it after the e.g. the session was started)
                    fvals = fixture.values()
                    if not fvals:
                        fvals = fixture.eval(None)
                    result_vector.append(fvals)
                    # will break only the inner loop
                    break
            elif scope_name == "function":
                result_vector.append(eval_fixture(fixture_name, test_class_instance))
                # will break only the inner loop
                break
            else:
                raise ValueError("unknown scope {}".format(scope_name))
    global _itertools_module
    if not _itertools_module:
        import itertools as _itertools_module
    return list(_itertools_module.product(*result_vector))

_skipped_marker = object()

class Mark:
    @staticmethod
    def usefixtures(*args):
        global _fixture_marks
        _fixture_marks += args
        def decorator(obj):
            return obj
        return decorator

    @staticmethod
    def xfail(cond, *args, **kwargs):
        def xfail_decorator(obj):
            if cond:
                def foo(self):
                    pass
                foo.__name__ = obj.__name__
                return foo
            return obj
        return xfail_decorator

def _pytest_fixture_maker(*args, **kwargs):
    def _fixture_decorator(fun):
        scope = kwargs.get("scope", "function")
        fixture = Fixture(fun, **kwargs)
        _fixture_scopes[scope][fixture.name()] = fixture
        return fun

    if len(args) == 1 and callable(args[0]):
        return _fixture_decorator(args[0])
    return _fixture_decorator

def _pytest_skip():
    raise SkipTest

_pytest_module = type(os)("pytest")
_pytest_module.mark = Mark()
_pytest_module.fixture = _pytest_fixture_maker
_pytest_module.raises = lambda x: TestCase.assertRaisesRegex(x, None)
_pytest_module.skip = _pytest_skip
sys.modules["pytest"] = _pytest_module

verbose = False


def _cleanup_tempdirs():
    import shutil
    # remove all created temp dirs
    global _created_tmp_dirs, verbose
    if verbose:
        print("Cleaning temp dirs: {!r}".format(_created_tmp_dirs))
    for tmp_dir in _created_tmp_dirs:
        shutil.rmtree(tmp_dir, ignore_errors=True)
    _created_tmp_dirs = []


print_lock = _thread.RLock()
class ThreadPool():
    cnt_lock = _thread.RLock()
    cnt = 0
    if os.environ.get(b"ENABLE_THREADED_GRAALPYTEST") == b"true":
        maxcnt = min(os.cpu_count(), 16)
        sleep = time.sleep
        start_new_thread = _thread.start_new_thread
        print("Running with %d threads" % maxcnt)
    else:
        sleep = lambda x: x
        start_new_thread = lambda f, args: f(*args)
        maxcnt = 1

    @classmethod
    def start(self, function):
        self.acquire_token()
        def runner():
            try:
                function()
            finally:
                self.release_token()
        self.start_new_thread(runner, ())
        self.sleep(0.5)

    @classmethod
    def acquire_token(self):
        while True:
            with self.cnt_lock:
                if self.cnt < self.maxcnt:
                    self.cnt += 1
                    break
            self.sleep(1)

    @classmethod
    def release_token(self):
        with self.cnt_lock:
            self.cnt -= 1

    @classmethod
    def shutdown(self):
        self.sleep(2)
        while self.cnt > 0:
            self.sleep(2)


def dump_truffle_ast(func):
    try:
        print(__dump_truffle_ast__(func))
    except:
        pass


class TestCase(object):

    def __init__(self):
        self.exceptions = []
        self.passed = 0
        self.failed = 0
        self.skipped_n = 0

    def get_useful_frame(self, tb):
        from traceback import extract_tb
        frame_summaries = extract_tb(tb)
        frame_summaries.reverse()
        for summary in frame_summaries:
            # Skip frame summary entries that refer to this file. These summaries will mostly be there because of the
            # assert functions and their location is not interesting.
            if summary[0] != __file__:
                return summary


    def run_safely(self, func, print_immediately=False):
        if verbose:
            with print_lock:
                print(u"\n\t\u21B3 ", func.__qualname__, " ", end="", flush=True)

        if func.__code__ is Mark.xfail(func).__code__:
            return _skipped_marker

        # insert fixture params
        co = func.__code__
        fixture_args = []
        if co.co_argcount > 0:
            arg_names = co.co_varnames[:co.co_argcount]
            if arg_names and arg_names[0] == "self":
                arg_names = arg_names[1:]
            fixture_args = get_fixture_values(self, arg_names)

        get_fixture_values(self, _fixture_marks)

        try:
            if fixture_args:
                for arg_vec in fixture_args:
                    func(*arg_vec)
            else:
                func()                
        except BaseException as e:
            if isinstance(e, SkipTest):
                return _skipped_marker
            else:
                if print_immediately:
                    print("Exception during setup occurred: %s\n" % e)
                code = func.__code__
                _, _, tb = sys.exc_info()
                try:
                    filename, line, func, text = self.get_useful_frame(tb)
                    self.exceptions.append(
                        ("In test '%s': %s:%d (%s)" % (code.co_filename, filename, line, func), e)
                    )
                except BaseException:
                    self.exceptions.append(
                        ("%s:%d (%s)" % (code.co_filename, code.co_firstlineno, func), e)
                    )
                return False
        else:
            return True


    def run_test(self, func):
        if "test_main" in str(func):
            pass
        elif not hasattr(func, "__call__"):
            pass
        else:
            def do_run():
                if hasattr(self, "setUp"):
                    if not self.run_safely(self.setUp, print_immediately=True):
                        self.failure(0)
                        return
                start = time.monotonic()
                r = self.run_safely(func)
                end = time.monotonic() - start
                if hasattr(self, "tearDown"):
                    if not self.run_safely(self.tearDown):
                        self.failure(end)
                with print_lock:
                    if r is _skipped_marker:
                        self.skipped()
                    else:
                        self.success(end) if r else self.failure(end)
            force_serial_execution = any(name in func.__qualname__ for name in SERIAL_TESTS)
            if force_serial_execution:
                ThreadPool.shutdown()
            ThreadPool.start(do_run)
            if force_serial_execution:
                ThreadPool.shutdown()

    def success(self, time):
        self.passed += 1
        if verbose:
            print("[%.3fs]" % time, end=" ")
        print(".", end="", flush=True)

    def failure(self, time):
        self.failed += 1
        fail_msg = FAIL + BOLD + "F" + ENDC if verbose else "F"
        if verbose:
            print("[%.3fs]" % time, end=" ")
        print(fail_msg, end="", flush=True)

    def skipped(self):
        self.skipped_n += 1
        print("s ", end="", flush=True)

    def assertIsInstance(self, value, cls):
        assert isinstance(value, cls), "Expected %r to be instance of %r" % (value, cls)

    def assertTrue(self, value, msg=""):
        assert value, msg

    def assertFalse(self, value, msg=""):
        assert not value, msg

    def assertIsNone(self, value, msg=""):
        if not msg:
            msg = "Expected '%r' to be None" % value
        assert value is None, msg

    def assertIsNotNone(self, value, msg=""):
        if not msg:
            msg = "Expected '%r' to not be None" % value
        assert value is not None, msg

    def assertIs(self, actual, expected, msg=""):
        if not msg:
            msg = "Expected '%r' to be '%r'" % (actual, expected)
        assert actual is expected, msg

    def assertIsNot(self, actual, expected, msg=""):
        if not msg:
            msg = "Expected '%r' not to be '%r'" % (actual, expected)
        assert actual is not expected, msg

    def assertEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be equal to '%r'" % (actual, expected)
        assert expected == actual, msg

    def assertNotEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to not be equal to '%r'" % (actual, expected)
        assert expected != actual, msg

    def assertAlmostEqual(self, expected, actual, msg=None):
        self.assertEqual(round(expected, 2), round(actual, 2), msg)

    def assertGreater(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be greater than '%r'" % (actual, expected)
        assert expected > actual, msg

    def assertGreaterEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be greater than or equal to '%r'" % (actual, expected)
        assert expected >= actual, msg

    def assertSequenceEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be equal to '%r'" % (actual, expected)
        assert len(expected) == len(actual), msg
        actual_iter = iter(actual)
        for expected_value in expected:
            assert expected_value == next(actual_iter), msg

    def assertIsInstance(self, obj, cls, msg=None):
        """Same as self.assertTrue(isinstance(obj, cls)), with a nicer
        default message."""
        if not isinstance(obj, cls):
            message = msg
            if msg is None:
                message = '%s is not an instance of %r' % (obj, cls)
            assert False, message

    def fail(self, msg):
        assert False, msg

    def skip(self):
        raise SkipTest

    def assertRaises(self, exc_type, function=None, *args, **kwargs):
        return self.assertRaisesRegex(exc_type, None, function, *args, **kwargs)

    class assertRaisesRegex():
        def __init__(self, exc_type, exc_regex, function=None, *args, **kwargs):
            import re
            function = function
            if function is None:
                self.exc_type = exc_type
                self.exc_regex = exc_regex
            else:
                try:
                    function(*args, **kwargs)
                except exc_type as exc:
                    if exc_regex:
                        assert re.search(exc_regex, str(exc)), "%s does not match %s" % (exc_regex, exc)
                else:
                    assert False, "expected '%r' to raise '%r'" % (function, exc_type)

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, traceback):
            self.type = exc_type
            self.value = exc
            self.tb = traceback
            if not exc_type:
                assert False, "expected '%r' to be raised" % self.exc_type
            else:
                if isinstance(self.exc_type, tuple):
                    for _exc_type in self.exc_type:
                        if self._match_exc(_exc_type, exc_type, exc):
                            return True
                elif self._match_exc(self.exc_type, exc_type, exc):
                    return True
            
        def _match_exc(self, expected_exc_type, actual_exc_type, actual_exc):
            import re
            if expected_exc_type in actual_exc_type.mro():
                self.exception = actual_exc
                if self.exc_regex:
                    assert re.search(self.exc_regex, str(actual_exc)), "%s does not match %s" % (self.exc_regex, actual_exc)
                return True


    def assertIn(self, expected, in_str, msg=""):
        if not msg:
            msg = "Expected '%r' to be in '%r'" % (expected, in_str)
        assert expected in in_str, msg

    @classmethod
    def run(cls, items=None):
        instance = cls()
        if items is None:
            items = []
            for typ in cls.mro():
                if typ is TestCase:
                    break
                items += typ.__dict__.items()
        if hasattr(instance, "setUpClass"):
            if not instance.run_safely(instance.setUpClass, print_immediately=True):
                return instance
        for k, v in items:
            if k.startswith("test"):
                testfn = getattr(instance, k, v)
                if patterns:
                    import fnmatch
                    name = getattr(testfn, '__qualname__', k)
                    if not any(fnmatch.fnmatch(name, p) for p in patterns):
                        continue
                instance.run_test(testfn)
        if hasattr(instance, "tearDownClass"):
            instance.run_safely(instance.tearDownClass)
        return instance

    @staticmethod
    def runClass(cls):
        if TestCase in cls.mro():
            return cls.run()
        class ThisTestCase(cls, TestCase): pass
        return ThisTestCase.run()


class TestRunner(object):

    def __init__(self, paths):
        self.testfiles = TestRunner.find_testfiles(paths[:])
        self.exceptions = []
        self.passed = 0
        self.failed = 0
        self.skipped_n = 0

    @staticmethod
    def find_testfiles(paths):
        testfiles = []
        while paths:
            path = paths.pop()
            if path.endswith(".py") and path.rpartition("/")[2].startswith("test_"):
                testfiles.append(path)
            else:
                try:
                    paths += [(path + f if path.endswith("/") else "%s/%s" % (path, f)) for f in os.listdir(path)]
                except OSError:
                    pass
        return testfiles

    @staticmethod
    def find_conftest(test_module_path):
        test_module_dir = dirname(test_module_path)
        if "conftest.py" in os.listdir(test_module_dir):
            return join(test_module_dir, "conftest.py")
        return None

    def load_module(self, path):
        name = path.rpartition("/")[2].partition(".")[0].replace('.py', '')
        directory = path.rpartition("/")[0]
        pkg = []
        while directory and any(f.endswith("__init__.py") for f in os.listdir(directory)):
            directory, slash, postfix = directory.rpartition("/")
            pkg.insert(0, postfix)
        if pkg:
            sys.path.insert(0, directory)
            try:
                test_module = __import__(".".join(pkg + [name]))
                for p in pkg[1:]:
                    test_module = getattr(test_module, p)
                test_module = getattr(test_module, name)
            except BaseException as e:
                _, _, tb = sys.exc_info()
                try:
                    from traceback import extract_tb
                    filename, line, func, text = extract_tb(tb)[-1]
                    self.exceptions.append(
                        ("In test '%s': Exception occurred in %s:%d" % (path, filename, line), e)
                    )
                except BaseException:
                    self.exceptions.append((path, e))
            else:
                return test_module
            finally:
                sys.path.pop(0)
        else:
            test_module = type(sys)(name, path)
            try:
                with _io.FileIO(path, "r") as f:
                    test_module.__file__ = path
                    exec(compile(f.readall(), path, "exec"), test_module.__dict__)
            except BaseException as e:
                self.exceptions.append((path, e))
            else:
                return test_module

    def test_modules(self):
        for testfile in self.testfiles:
            yield self.load_module(testfile)

    def load_conftest(self, testfile):
        self.load_module(testfile)

    def run(self):
        # eval session scope
        eval_scope("session")

        testcases = []
        for module in self.test_modules():
            if module is None:
                continue
            # eval module scope
            eval_scope("module")

            # load conftest if exists
            conftest = TestRunner.find_conftest(module.__file__)
            if conftest and not conftest in _loaded_conftest:
                self.load_conftest(conftest)

            if verbose:
                print(u"\n\u25B9 ", module.__name__, end="")
            # some tests can modify the global scope leading to a RuntimeError: test_scope.test_nesting_plus_free_ref_to_global
            module_dict = dict(module.__dict__)
            for k, v in module_dict.items():
                if (k.startswith("Test") or k.endswith("Test") or k.endswith("Tests")) and isinstance(v, type):
                    testcases.append(TestCase.runClass(v))
                else:
                    testcases.append(TestCase.run(items=[(k, v)]))
            if verbose:
                with print_lock:
                    print()
        ThreadPool.shutdown()
        for testcase in testcases:
            self.exceptions += testcase.exceptions
            self.passed += testcase.passed
            self.failed += testcase.failed
            self.skipped_n += testcase.skipped_n

        print("\n\nRan %d tests (%d passes, %d failures, %d skipped)" % (self.passed + self.failed, self.passed, self.failed, self.skipped_n))
        for e in self.exceptions:
            msg, exc = e
            print(msg)
            if verbose:
                try:
                    import traceback
                    traceback.print_exception(type(exc), exc, exc.__traceback__)
                except Exception:
                    pass
            else:
                print(exc)

        if self.exceptions or self.failed:
            _cleanup_tempdirs()
            os._exit(1)


def skipIf(boolean, *args, **kwargs):
    return skipUnless(not boolean, *args, **kwargs)


def skipUnless(boolean, msg=""):
    if not boolean:
        def decorator(f):
            def wrapper(*args, **kwargs):
                pass
            return wrapper
    else:
        def decorator(f):
            return f
    return decorator


class TextTestResult():
    "Just a dummy to satisfy the unittest.support import"
    pass


if __name__ == "__main__":
    sys.modules["unittest"] = sys.modules["__main__"]
    patterns = []
    argv = sys.argv[:]
    idx = 0
    while idx < len(argv):
        if argv[idx] == "-k":
            argv.pop(idx)
            try:
                pattern = argv.pop(idx)
                if '*' not in pattern:
                    pattern = '*' + pattern + '*'
                patterns.append(pattern)
            except IndexError:
                print("-k needs an argument")
        else:
            idx += 1

    if len(argv) > 1 and argv[1] == "-v":
        verbose = True
        paths = argv[2:]
    else:
        verbose = False
        paths = argv[1:]

    python_paths = set()
    for pth in paths:
        module_path = pth
        if pth.endswith('.py'):
            module_path = pth.rsplit('/', 1)[0]
        python_paths.add(module_path)

    for pth in python_paths:
        sys.path.append(pth)

    try:
        TestRunner(paths).run()
    finally:
        _cleanup_tempdirs()

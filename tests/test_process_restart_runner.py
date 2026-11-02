import subprocess
import unittest
from unittest.mock import patch

from run_process_restart import run_probe


class ProcessProbeRunnerTests(unittest.TestCase):
    @patch("run_process_restart.subprocess.run")
    def test_accepts_one_successful_test(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "OK (1 test)\nINSTRUMENTATION_CODE: -1", "")
        run_probe("verify")
        self.assertIn("org.alquds.library.ProcessRestartProbe#verify", run.call_args.args[0])

    @patch("run_process_restart.subprocess.run")
    def test_zero_exit_code_does_not_hide_junit_failure(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "FAILURES!!!\nTests run: 1, Failures: 1", "")
        with self.assertRaises(RuntimeError):
            run_probe("verify")

    @patch("run_process_restart.subprocess.run")
    def test_no_matching_tests_cannot_pass(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "OK (0 tests)", "")
        with self.assertRaises(RuntimeError):
            run_probe("prepare")

    @patch("run_process_restart.subprocess.run")
    def test_runner_crash_cannot_pass(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "INSTRUMENTATION_FAILED: Process crashed.", "")
        with self.assertRaises(RuntimeError):
            run_probe("verify")

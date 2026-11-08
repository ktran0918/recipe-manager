from pathlib import Path

# guardrails-ai phones home to its own telemetry endpoint on every validator run
# unless ~/.guardrailsrc disables it — the only reliable opt-out found: in-process
# settings (settings.rc.enable_metrics = False) don't take effect because the
# telemetry client is a singleton that reads the rc file once, lazily, on first
# use, by which point the override is too late. Writing the file ourselves avoids
# the interactive `guardrails configure` CLI step at image build or app startup.
_RC_CONTENT = "enable_metrics=false\nuse_remote_inferencing=false\n"


def disable_guardrails_telemetry() -> None:
    """Must run before `guardrails` is imported anywhere else in the process."""
    rc_path = Path.home() / ".guardrailsrc"
    if not rc_path.exists():
        rc_path.write_text(_RC_CONTENT)

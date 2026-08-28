from pathlib import Path

from ire.cli import main


def test_validate_config_command_returns_error_on_missing_files(tmp_path: Path) -> None:
    rc = main(["validate-config", "--config-dir", str(tmp_path)])
    assert rc == 1

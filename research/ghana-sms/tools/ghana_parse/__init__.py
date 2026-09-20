"""Ghana mobile-money SMS extraction reference implementation.

Pure standard library, no third-party dependencies, so the numbers reported in the
paper can be reproduced on any machine with Python 3.9+.

The authoritative extraction specification is ``spec/ghana_patterns.json``. The
Kotlin production parser is generated from the same file
(``tools/sync_kotlin.py``), so the Python reference and the Android implementation
cannot drift apart.
"""

from .spec import Spec, load_spec
from .engine import GhanaSmsEngine, ExtractionResult
from .normalize import (
    normalize_amount,
    normalize_date,
    normalize_name,
    normalize_phone,
    normalize_trx_id,
    normalize_time,
    normalize_channel,
)

__all__ = [
    "Spec",
    "load_spec",
    "GhanaSmsEngine",
    "ExtractionResult",
    "normalize_amount",
    "normalize_date",
    "normalize_name",
    "normalize_phone",
    "normalize_trx_id",
    "normalize_time",
    "normalize_channel",
]

#!/usr/bin/env python3
"""Build-independent JSON profile and signal-shape validation."""
import json
import re
from pathlib import Path
profiles = json.loads((Path(__file__).parents[1] / 'app/src/main/assets/profiles.json').read_text())
assert len(profiles) >= 3
ids = [p['id'] for p in profiles]
assert len(set(ids)) == len(ids)
count = 0
for profile in profiles:
    assert profile['name'] and profile['brand']
    for key, cmd in profile['commands'].items():
        assert re.fullmatch('[A-Z0-9_]+', key)
        assert 20000 <= cmd['frequency'] <= 60000
        if 'pattern' in cmd:
            timings = cmd['pattern']
        else:
            proto = cmd['protocol']
            assert proto in ('NEC', 'SAMSUNG', 'SONY12')
            code = cmd['code']
            assert re.fullmatch('[0-9A-Fa-f]{1,8}', code)
            bits = 12 if proto == 'SONY12' else 32
            assert int(code, 16) < 2 ** bits
            n = int(code, 16)
            b = [((n >> (bits - i - 1 if cmd['order'] == 'MSB' else i)) & 1) for i in range(bits)]
            if proto == 'SONY12':
                timings = [2400, 600] + [v for bit in b for v in (1200 if bit else 600, 600)]
            else:
                timings = ([4500, 4500] if proto == 'SAMSUNG' else [9000, 4500]) + [v for bit in b for v in (560, 1690 if bit else 560)] + [560]
        assert len(timings) <= 500
        assert all(0 < d <= 120000 for d in timings)
        assert sum(timings) < 2_000_000
        count += 1
print(f'OK: {len(profiles)} perfis, {count} sinais válidos')

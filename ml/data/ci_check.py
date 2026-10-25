"""CI entry point: explicit external Day 5 inputs only; absence fails closed."""
import os
from ml.data.split_tool import check


def main():
    required = ['METIS_TRAIN_CSV', 'METIS_EXTERNAL_TEST_CSV', 'METIS_TEST_MANIFEST']
    missing = [key for key in required if not os.environ.get(key)]
    if missing:
        raise SystemExit('independent-test evidence not found: ' + ', '.join(missing))
    print(check(*(os.environ[key] for key in required)))


if __name__ == '__main__':
    main()

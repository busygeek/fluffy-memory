import csv
import os

INPUT_CSV = "Avaya PBX Vector.csv"   # <-- change to your actual filename
OUTPUT_DIR = "avaya_vectors_output"

os.makedirs(OUTPUT_DIR, exist_ok=True)

# Fixed columns (A=0 to G=6)
FIXED_COLS = {
    0: "Number",
    1: "Name",
    2: "Native Name",
    3: "Multimedia",
    4: "Lock",
    5: "Attendant Vectoring?",
    6: "Meet-me Conference?",
}

# Steps start at column index 7 (H), each step = 87 columns
STEP_START_COL = 7
STEP_WIDTH = 87

# Known step-starting keywords (first non-empty token of a step)
STEP_KEYWORDS = {
    "wait-time", "goto", "announcement", "collect", "route-to",
    "disconnect", "stop", "busy", "check-back", "consider",
    "adjunct", "messaging", "queue-to", "reply", "set",
    "converse-on", "wait", "return", "label", "pause"
}


def clean_tokens(cells):
    return [c.strip() for c in cells if c.strip()]


def format_step(step_num, tokens):
    if not tokens:
        return None

    keyword = tokens[0].lower().lstrip("#")
    rest = tokens[1:]
    prefix = "#" if tokens[0].startswith("#") else " "

    if keyword == "wait-time":
        line = f"{prefix}{step_num:02d} wait-time    {' '.join(rest)}"
    elif keyword == "goto":
        line = f"{prefix}{step_num:02d} goto step    {' '.join(rest)}"
    elif keyword == "announcement":
        line = f"{prefix}{step_num:02d} announcement {' '.join(rest)}"
    elif keyword == "collect":
        line = f"{prefix}{step_num:02d} collect      {' '.join(rest)}"
    elif keyword == "route-to":
        line = f"{prefix}{step_num:02d} route-to     {' '.join(rest)}"
    elif keyword == "disconnect":
        line = f"{prefix}{step_num:02d} disconnect   {' '.join(rest)}"
    elif keyword == "stop":
        line = f"{prefix}{step_num:02d} stop"
    elif keyword == "busy":
        line = f"{prefix}{step_num:02d} busy         {' '.join(rest)}"
    elif keyword == "queue-to":
        line = f"{prefix}{step_num:02d} queue-to     {' '.join(rest)}"
    else:
        line = f"{prefix}{step_num:02d} {' '.join(tokens)}"

    return line


def format_vector(row_dict, steps_raw):
    number  = row_dict.get("Number", "?")
    name    = row_dict.get("Name", "")
    native  = row_dict.get("Native Name", "")
    mm      = row_dict.get("Multimedia", "n")
    lock    = row_dict.get("Lock", "n")
    att_vec = row_dict.get("Attendant Vectoring?", "n")
    meet_me = row_dict.get("Meet-me Conference?", "n")

    lines = []
    lines.append("=" * 70)
    lines.append("                        CALL VECTOR")
    lines.append("=" * 70)
    lines.append(f"Number: {number:<10}  Name: {name}  Native Name: {native}")
    lines.append(
        f"Multimedia? {mm}   Attendant Vectoring? {att_vec}   "
        f"Meet-me Conf? {meet_me}   Lock? {lock}"
    )
    lines.append("-" * 70)

    step_num = 1
    for chunk in steps_raw:
        tokens = clean_tokens(chunk)
        if not tokens:
            continue
        formatted = format_step(step_num, tokens)
        if formatted:
            lines.append(formatted)
            step_num += 1

    lines.append("=" * 70)
    return "\n".join(lines)


def process_csv(filepath):
    with open(filepath, newline='', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        next(reader)  # skip header row

        all_output = []
        count = 0

        for row in reader:
            if not any(c.strip() for c in row):
                continue  # skip blank rows

            # Map fixed columns A-G
            row_dict = {}
            for idx, col_name in FIXED_COLS.items():
                row_dict[col_name] = row[idx].strip() if idx < len(row) else ""

            # Slice step data starting at col H (index 7)
            step_data = row[STEP_START_COL:]

            # Pad to ensure we can slice cleanly
            # Split into 87-column chunks
            steps_raw = []
            for i in range(0, len(step_data), STEP_WIDTH):
                chunk = step_data[i:i + STEP_WIDTH]
                tokens = clean_tokens(chunk)
                if tokens:  # only keep non-empty steps
                    steps_raw.append(chunk)

            if not steps_raw:
                continue

            vector_num = row_dict.get("Number", "unknown").strip()
            formatted = format_vector(row_dict, steps_raw)
            all_output.append(formatted)
            count += 1

            # Save individual file per vector
            safe_name = vector_num.replace("/", "_").replace("\\", "_")
            out_file = os.path.join(OUTPUT_DIR, f"vector_{safe_name}.txt")
            with open(out_file, "w", encoding="utf-8") as f_out:
                f_out.write(formatted)
            print(f"  Written: {out_file}")

        # Save all-in-one combined file
        combined_path = os.path.join(OUTPUT_DIR, "_ALL_VECTORS.txt")
        with open(combined_path, "w", encoding="utf-8") as f_all:
            f_all.write("\n\n".join(all_output))

        print(f"\nAll vectors combined: {combined_path}")
        print(f"Total vectors processed: {count}")


if __name__ == "__main__":
    if not os.path.exists(INPUT_CSV):
        print(f"ERROR: File not found: '{INPUT_CSV}'")
        print("Edit the INPUT_CSV variable at the top of this script.")
    else:
        print(f"Processing: {INPUT_CSV}")
        process_csv(INPUT_CSV)
        print("Done!")

import csv
import re
import os

INPUT_CSV = "Avaya PBX Vector.csv"   # <-- change to your actual filename
OUTPUT_DIR = "avaya_vectors_output"

os.makedirs(OUTPUT_DIR, exist_ok=True)

# Known Avaya vector step keywords to detect step boundaries
STEP_KEYWORDS = [
    "wait-time", "goto", "announcement", "collect", "route-to",
    "disconnect", "stop", "busy", "check-back", "consider",
    "adjunct", "messaging", "queue-to", "reply", "set",
    "converse-on", "wait", "return", "label"
]

def clean_tokens(cells):
    """Remove empty strings and strip whitespace from cell list."""
    return [c.strip() for c in cells if c.strip()]

def split_into_steps(tokens):
    """
    Split the flat token list into individual steps.
    Each step starts with a known keyword.
    """
    steps = []
    current = []
    for token in tokens:
        if token.lower() in STEP_KEYWORDS and current:
            steps.append(current)
            current = [token]
        else:
            current.append(token)
    if current:
        steps.append(current)
    return steps

def format_step(step_num, tokens):
    """Format a single step's tokens into a readable line."""
    if not tokens:
        return ""
    keyword = tokens[0].lower()
    rest = tokens[1:]

    # Build line based on keyword type
    if keyword == "wait-time":
        # wait-time 0 secs hearing ringback
        line = f"{step_num:02d} wait-time    {' '.join(rest)}"

    elif keyword == "goto":
        # goto step X if <condition>
        # tokens: goto, step, N, if, condition...
        line = f"{step_num:02d} goto step    {' '.join(rest)}"

    elif keyword == "announcement":
        # announcement #XXXXXXX
        line = f"{step_num:02d} announcement {' '.join(rest)}"

    elif keyword == "collect":
        # collect N digits after announcement XXXXX for none
        line = f"{step_num:02d} collect      {' '.join(rest)}"

    elif keyword == "route-to":
        # route-to number XXXXX cov n if digit = N
        marker = "#" if tokens and tokens[0].startswith("#") else ""
        line = f"{step_num:02d} route-to     {' '.join(rest)}"

    elif keyword == "disconnect":
        line = f"{step_num:02d} disconnect   {' '.join(rest)}"

    elif keyword == "stop":
        line = f"{step_num:02d} stop"

    else:
        line = f"{step_num:02d} {' '.join(tokens)}"

    return line

def format_vector(row_dict, step_tokens):
    """Build the full formatted vector block."""
    number   = row_dict.get("Number", "?")
    name     = row_dict.get("Name", "")
    native   = row_dict.get("Native Name", "")
    mm       = row_dict.get("Multimedia", "n")
    lock     = row_dict.get("Lock", "n")
    att_vec  = row_dict.get("Attendant Vectoring?", "n")
    meet_me  = row_dict.get("Meet-me Conference?", "n")

    steps = split_into_steps(step_tokens)

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

    for i, step_tokens in enumerate(steps, start=1):
        lines.append(format_step(i, step_tokens))

    lines.append("=" * 70)
    return "\n".join(lines)


def process_csv(filepath):
    with open(filepath, newline='', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        raw_headers = next(reader)  # first row = headers

        # Fixed columns (A-G)
        fixed_cols = ["Number", "Name", "Native Name", "Multimedia",
                      "Lock", "Attendant Vectoring?", "Meet-me Conference?"]

        all_output = []

        for row in reader:
            if not any(c.strip() for c in row):
                continue  # skip fully empty rows

            # Map fixed columns
            row_dict = {}
            for i, col in enumerate(fixed_cols):
                row_dict[col] = row[i].strip() if i < len(row) else ""

            # Everything from column H (index 7) onwards = step data
            step_cells = row[7:] if len(row) > 7 else []
            step_tokens = clean_tokens(step_cells)

            if not step_tokens:
                continue

            vector_num = row_dict.get("Number", "unknown")
            formatted = format_vector(row_dict, step_tokens)
            all_output.append(formatted)

            # Save individual file
            out_file = os.path.join(OUTPUT_DIR, f"vector_{vector_num}.txt")
            with open(out_file, "w", encoding="utf-8") as f_out:
                f_out.write(formatted)
            print(f"  Written: {out_file}")

        # Also save all-in-one file
        combined_path = os.path.join(OUTPUT_DIR, "_ALL_VECTORS.txt")
        with open(combined_path, "w", encoding="utf-8") as f_all:
            f_all.write("\n\n".join(all_output))
        print(f"\nAll vectors combined: {combined_path}")
        print(f"Total vectors processed: {len(all_output)}")


if __name__ == "__main__":
    if not os.path.exists(INPUT_CSV):
        print(f"ERROR: File not found: {INPUT_CSV}")
        print("Edit INPUT_CSV variable at top of script to match your filename.")
    else:
        print(f"Processing: {INPUT_CSV}")
        process_csv(INPUT_CSV)
        print("Done!")

import argparse
import csv
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

PHRASES_60 = [
    (1, 'remind me tomorrow to call Rahul'),
    (2, 'remind me to drink water'),
    (3, 'rahul ko kal 8 baje call karna yaad dilao'),
    (4, 'remnd me to submit lab report at 6'),
    (5, 'add task finish DSP assignment'),
    (6, 'mujhe kal gym jaana hai task bana do'),
    (7, 'I promised Rahul the slides by Friday'),
    (8, 'what did I promise Rahul'),
    (9, 'what is pending in the RF project'),
    (10, 'what should I do now'),
    (11, 'set a timer for 10 minutes'),
    (12, 'wake me at 6 am'),
    (13, 'call Rahul'),
    (14, "text mom I'll be late"),
    (15, 'move my DSP task to tomorrow'),
    (16, 'mark lab report done'),
    (17, 'make DSP assignment high priority'),
    (18, 'rename DSP assignment to DSP lab 3'),
    (19, 'plan my day'),
    (20, 'how was my week'),
    (21, 'delete everything'),
    (22, 'delete all tasks'),
    (23, 'delete it'),
    (24, 'finish it'),
    (25, 'cancel that'),
    (26, 'remove the task'),
    (27, 'delete the DSP task'),
    (28, 'mark all done'),
    (29, 'clear my calendar'),
    (30, 'call him'),
    (31, 'send him that'),
    (32, 'call Raghav'),
    (33, 'postpone it'),
    (34, 'delet the dsp taks'),
    (35, "don't delete my tasks"),
    (36, "never mind don't remind me"),
    (37, "call Rahul and text mom I'll be late"),
    (38, 'remind me to call Rahul and delete the lab task'),
    (39, 'forget about the gym task'),
    (40, 'reset everything'),
    (41, 'asdfgh jkl'),
    (42, "what's the weather in Chennai"),
    (43, 'tell me a joke'),
    (44, 'who won the match yesterday'),
    (45, 'play some music'),
    (46, 'book me an Uber'),
    (47, 'order pizza'),
    (48, 'transfer 5000 rupees to Rahul'),
    (49, 'turn off wifi'),
    (50, "what's the capital of France"),
    (51, 'uninstall Instagram'),
    (52, 'read my WhatsApp messages'),
    (53, 'post on Instagram'),
    (54, 'hello'),
    (55, 'thanks'),
    (56, "I'm feeling really down today"),
    (57, 'what time is it'),
    (58, 'Rahul'),
    (59, 'tomorrow'),
    (60, 'call'),
]

PHRASES_12 = [
    (61, 'set timer 10 min'),
    (62, 'timer 5 mins'),
    (63, 'timer 30 sec'),
    (64, 'timer 2 hrs'),
    (65, 'timer for 1 hr'),
    (66, '10m timer'),
    (67, '1.5 hr timer'),
    (68, 'timer for aadha ghanta'),
    (69, 'wake me at 6'),
    (70, 'alarm at 6 am'),
    (71, 'kal 8 baje alarm laga do'),
    (72, 'subah 6 baje utha dena'),
]

CLARIFICATION_PAIRS = [
    ('a', 'Remind me tomorrow to call Rahul', '8pm'),
    ('b', 'Remind me Friday to buy milk', 'at 9'),
    ('c', 'kal call karna hai Rahul ko remind karo', 'subah 10 baje'),
    ('d', 'wake me at 6', 'am'),
    ('e', 'kal 8 baje alarm laga do', 'subah'),
    ('f', 'set alarm for 7', 'pm'),
    ('g', 'call him', 'David S'),
    ('h', 'call her', 'Priya'),
    ('i', 'wake me at 6', 'set timer 10m'),
    ('j', 'wake me at 6', 'never mind'),
    ('k', 'I promised Rahul the slides', 'Friday'),
    ('l', 'I promised Rahul the slides', 'no deadline'),
]

ALL_PHRASES = {pid: text for pid, text in PHRASES_60 + PHRASES_12}

ALLOWED_NAMES = {'rahul', 'raghav', 'rahul k', 'david s', 'priya'}


def sanitize_text(text: str) -> str:
    # Replace phone numbers with <number>
    # Phone numbers typically 7-15 digits, possibly with +, -, space
    text = re.sub(r'(\+?\d[\d -]{8,}\d)', '<number>', text)
    m = re.search(r'(Multiple contacts found for "[^"]+": )(.*)( \(type a fuller name\)\. Which one did you mean\?)', text)
    if m:
        candidates = m.group(2).split(' or ')
        redacted = [c if c.strip().lower() in ALLOWED_NAMES else '<contact>' for c in candidates]
        text = m.group(1) + ' or '.join(redacted) + m.group(3)
    m_no_phone = re.search(r'No phone number found for (.+?)\.', text)
    if m_no_phone:
        name = m_no_phone.group(1)
        if name.strip().lower() not in ALLOWED_NAMES:
            if "'s Mom" in name or "'s mom" in name:
                text = text.replace(name, "<contact>'s Mom")
            else:
                text = text.replace(name, "<contact>")
    return text


def run_adb(args, timeout=15):
    try:
        res = subprocess.run(['adb'] + args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        return res.stdout.decode('utf-8', errors='ignore')
    except Exception as e:
        return ''


def ensure_device_ready():
    subprocess.run(['adb', 'wait-for-device'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    run_adb(['shell', 'cmd', 'statusbar', 'collapse'])
    time.sleep(0.3)
    focus = get_current_focus()
    while 'NotificationShade' in focus:
        run_adb(['shell', 'cmd', 'statusbar', 'collapse'])
        run_adb(['shell', 'input', 'keyevent', '4'])
        time.sleep(0.5)
        focus = get_current_focus()


def escape_for_input_text(phrase: str) -> str:
    out = []
    for ch in phrase:
        if ch == ' ':
            out.append('%s')
        elif ch == "'":
            out.append("\\'")
        elif ch == '"':
            out.append('\\"')
        elif ch in ['&', '(', ')', '<', '>', '|', ';', '$', '*', '?', '!', '~', '`']:
            out.append(f'\\{ch}')
        else:
            out.append(ch)
    return ''.join(out)


def get_current_focus():
    out = run_adb(['shell', 'dumpsys', 'window'])
    for line in out.splitlines():
        if 'mCurrentFocus' in line:
            return line.strip()
    return ''


def get_ui_dump():
    run_adb(['shell', 'uiautomator', 'dump', '/sdcard/window_dump.xml'])
    xml_str = run_adb(['shell', 'cat', '/sdcard/window_dump.xml'])
    try:
        return ET.fromstring(xml_str)
    except Exception as e:
        return None


def parse_response_nodes(root, sent_phrase):
    if root is None:
        return []
    
    # Filter standard chrome/system texts
    ignored = {
        'M E T I S', 'METIS', 'Ask METIS…', 'Temporary chat · local agent',
        'Temporary chat', 'Today', 'All tasks', 'Good evening.', 'Good morning.',
        'Good afternoon.', 'What can I help with?',
        'A little clarity. A more intentional day.',
        'This conversation is temporary. Your saved data stays on your device.',
        'Plan my day', 'What should I do?', '↗', 'Open navigation', 'More options',
        'Use voice', 'Send request'
    }

    nodes_info = []
    for n in root.iter('node'):
        t = n.attrib.get('text', '').strip()
        d = n.attrib.get('content-desc', '').strip()
        b = n.attrib.get('bounds', '')
        c = n.attrib.get('class', '')
        if t and t not in ignored and t != sent_phrase:
            nodes_info.append({'text': t, 'desc': d, 'bounds': b, 'class': c})
    return nodes_info


def classify_response(nodes, focus_str, sent_phrase):
    combined_text = '\n'.join([n['text'] for n in nodes])
    
    # Check if external app opened
    if 'dev.metis.agent' not in focus_str and focus_str:
        if 'dialer' in focus_str.lower() or 'phone' in focus_str.lower():
            return 'PROPOSAL', 'call external app', combined_text
        if 'mms' in focus_str.lower() or 'messaging' in focus_str.lower() or 'sms' in focus_str.lower():
            return 'PROPOSAL', 'sms external app', combined_text
        if 'deskclock' in focus_str.lower() or 'alarm' in focus_str.lower() or 'timer' in focus_str.lower():
            return 'PROPOSAL', 'clock external app', combined_text
        return 'EXTERNAL_APP', focus_str, combined_text

    # Check for proposal card
    is_proposal = any('Proposed action' in n['text'] or 'Accept proposal' in n['text'] for n in nodes)
    
    # Specific detail extraction
    if is_proposal:
        # Determine proposal type
        prop_type = 'unknown'
        detail = ''
        for n in nodes:
            t = n['text']
            if 'timer' in t.lower() or 'Duration:' in t:
                prop_type = 'TIMER'
                dur_m = re.search(r'Duration:\s*([^\n]+)', combined_text)
                detail = dur_m.group(0) if dur_m else t
            elif 'alarm' in t.lower() or 'Time:' in t:
                prop_type = 'ALARM'
                time_m = re.search(r'Time:\s*([^\n]+)', combined_text)
                detail = time_m.group(0) if time_m else t
            elif 'call' in t.lower() or 'Call ' in t:
                prop_type = 'CALL'
                detail = t
            elif 'message' in t.lower() or 'sms' in t.lower() or 'Send SMS' in t:
                prop_type = 'SMS'
                detail = t
            elif 'task' in t.lower():
                if 'delete' in t.lower() or 'Delete' in t:
                    prop_type = 'DELETE_TASK'
                elif 'mark' in t.lower() or 'complete' in t.lower() or 'Complete' in t:
                    prop_type = 'COMPLETE_TASK'
                elif 'postpone' in t.lower() or 'move' in t.lower():
                    prop_type = 'POSTPONE_TASK'
                elif 'rename' in t.lower():
                    prop_type = 'RENAME_TASK'
                elif 'priority' in t.lower():
                    prop_type = 'PRIORITIZE_TASK'
                else:
                    prop_type = 'CREATE_TASK'
                detail = t
            elif 'reminder' in t.lower() or 'Remind' in t:
                prop_type = 'CREATE_REMINDER'
                detail = t
            elif 'promise' in t.lower() or 'Promise' in t:
                prop_type = 'CREATE_PROMISE'
                detail = t
                
        if detail == '' and nodes:
            detail = nodes[0]['text']
        return 'PROPOSAL', f'{prop_type} ({detail})', combined_text

    # Check for clarification / questions
    clarify_patterns = [
        r'who would you like to call',
        r'who do you mean',
        r'what time',
        r'which task',
        r'specify a recipient',
        r'do you want to',
        r'am or pm',
        r'did you mean',
        r'which one',
        r'\?'
    ]
    for pat in clarify_patterns:
        m = re.search(pat, combined_text, re.IGNORECASE)
        if m:
            # Extract the question line
            for line in combined_text.splitlines():
                if re.search(pat, line, re.IGNORECASE):
                    return 'CLARIFICATION', line.strip(), combined_text
            return 'CLARIFICATION', m.group(0), combined_text

    # Check for Fallback / safe read failure
    if 'cannot safely read' in combined_text or 'I cannot safely read' in combined_text:
        first_line = combined_text.splitlines()[0] if combined_text else 'cannot safely read'
        return 'FALLBACK', first_line, combined_text

    # Check for Unsupported
    if any(k in combined_text.lower() for k in ['not supported', "i don't support", "doesn't support", 'cannot perform', 'unsupported']):
        first_line = combined_text.splitlines()[0] if combined_text else 'unsupported'
        return 'UNSUPPORTED', first_line, combined_text

    # Check for No-action / Negation
    if any(k in combined_text.lower() for k in ["won't delete", "cancelled", "never mind", "no changes", "ok, i won't", "okay, i won't", "understood, no action"]):
        return 'NO_ACTION', combined_text.splitlines()[0] if combined_text else 'no action', combined_text

    # Check for Answer
    # e.g., listing tasks, commitments, status, suggestions, etc.
    if any(k in combined_text.lower() for k in ['promise', 'pending', 'suggest', 'plan', 'reflection', 'tasks', 'here are', 'no tasks', 'no promises']):
        return 'ANSWER', combined_text.splitlines()[0] if combined_text else 'answer', combined_text

    return 'OTHER', combined_text.splitlines()[0] if combined_text else '', combined_text


def find_input_box(root):
    if root is not None:
        for n in root.iter('node'):
            if 'EditText' in n.attrib.get('class', '') or 'Ask METIS' in n.attrib.get('text', ''):
                b = n.attrib.get('bounds', '')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                if m:
                    return (int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2
    return 540, 2052


def find_send_button(root):
    if root is not None:
        for n in root.iter('node'):
            if n.attrib.get('content-desc') == 'Send request':
                b = n.attrib.get('bounds', '')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                if m:
                    return (int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2
    return 960, 1467


def send_turn(phrase: str):
    root_before = get_ui_dump()
    ix, iy = find_input_box(root_before)
    run_adb(['shell', 'input', 'tap', str(ix), str(iy)])
    time.sleep(0.3)
    
    escaped = escape_for_input_text(phrase)
    run_adb(['shell', 'input', 'text', escaped])
    time.sleep(0.3)
    
    root_typed = get_ui_dump()
    sx, sy = find_send_button(root_typed)
    t0 = time.time()
    run_adb(['shell', 'input', 'tap', str(sx), str(sy)])
    
    stable_cycles = 0
    last_text = ''
    root = None
    focus = ''
    time.sleep(1.0)
    for _ in range(20):
        focus = get_current_focus()
        if 'dev.metis.agent' not in focus and focus:
            break
        root = get_ui_dump()
        nodes = parse_response_nodes(root, phrase)
        curr_text = ' '.join(n['text'] for n in nodes)
        if curr_text and curr_text == last_text:
            stable_cycles += 1
            if stable_cycles >= 2:
                break
        elif curr_text:
            last_text = curr_text
            stable_cycles = 1
        time.sleep(0.5)
        
    latency_ms = int((time.time() - t0) * 1000)
    if root is None:
        root = get_ui_dump()
    nodes = parse_response_nodes(root, phrase)
    resp_class, detail, full_text = classify_response(nodes, focus, phrase)
    return resp_class, detail, full_text, latency_ms, root, focus


def dismiss_ui(root, focus):
    if root is not None:
        for n in root.iter('node'):
            if n.attrib.get('text') == 'Cancel':
                b = n.attrib.get('bounds', '')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                if m:
                    cx = (int(m.group(1)) + int(m.group(3))) // 2
                    cy = (int(m.group(2)) + int(m.group(4))) // 2
                    run_adb(['shell', 'input', 'tap', str(cx), str(cy)])
                    time.sleep(0.3)
                break
    if 'dev.metis.agent' not in focus and focus:
        run_adb(['shell', 'input', 'keyevent', '4'])
        time.sleep(0.3)
        run_adb(['shell', 'input', 'keyevent', '4'])


def execute_clarification_pair(pair_id, turn1_phrase, turn2_phrase, build_name, temp_dir):
    print(f'[{build_name}] Clarification {pair_id}: "{turn1_phrase}" -> "{turn2_phrase}"')
    ensure_device_ready()
    
    # 1. Force-stop and restart app
    run_adb(['shell', 'am', 'force-stop', 'dev.metis.agent'])
    time.sleep(0.3)
    run_adb(['shell', 'monkey', '-p', 'dev.metis.agent', '1'])
    time.sleep(1.0)
    
    # Turn 1
    t1_class, t1_detail, t1_full, t1_lat, r1, f1 = send_turn(turn1_phrase)
    ss_t1 = os.path.join(temp_dir, f'{build_name}_clarify_{pair_id}_t1.png')
    try:
        subprocess.run(f'adb exec-out screencap -p > "{ss_t1}"', shell=True, timeout=10)
    except Exception:
        pass
    t1_sanitized = sanitize_text(t1_full.replace('\n', ' -- '))
    print(f'   Turn 1 -> Class: {t1_class} | Resp: {t1_sanitized}')
    
    # Turn 2
    t2_class, t2_detail, t2_full, t2_lat, r2, f2 = send_turn(turn2_phrase)
    ss_t2 = os.path.join(temp_dir, f'{build_name}_clarify_{pair_id}_t2.png')
    try:
        subprocess.run(f'adb exec-out screencap -p > "{ss_t2}"', shell=True, timeout=10)
    except Exception:
        pass
    
    dismiss_ui(r2, f2)
    
    t2_sanitized = sanitize_text(t2_full.replace('\n', ' -- '))
    t2_detail_san = sanitize_text(t2_detail.replace('\n', ' '))
    print(f'   Turn 2 -> Class: {t2_class} | Detail: {t2_detail_san} | Resp: {t2_sanitized}')
    
    return {
        'id': pair_id,
        'turn1_phrase': turn1_phrase,
        'turn1_class': t1_class,
        'turn1_response': t1_sanitized,
        'turn2_phrase': turn2_phrase,
        'turn2_class': t2_class,
        'turn2_detail': t2_detail_san,
        'turn2_response': t2_sanitized,
        'build': build_name,
        'screenshot_t1': ss_t1,
        'screenshot_t2': ss_t2,
    }


def execute_phrase(pid, phrase, build_name, temp_dir):
    print(f'[{build_name}] Row {pid}: "{phrase}"')
    ensure_device_ready()
    
    # 1. Force-stop and restart app
    run_adb(['shell', 'am', 'force-stop', 'dev.metis.agent'])
    time.sleep(0.3)
    run_adb(['shell', 'monkey', '-p', 'dev.metis.agent', '1'])
    time.sleep(1.0)
    
    # 2. Tap input
    run_adb(['shell', 'input', 'tap', '540', '2052'])
    time.sleep(0.3)
    
    # Type phrase
    escaped = escape_for_input_text(phrase)
    run_adb(['shell', 'input', 'text', escaped])
    time.sleep(0.3)
    
    # 3. Tap Send & measure latency
    t0 = time.time()
    # Find send button bounds or default to 960, 1467
    root_before = get_ui_dump()
    send_x, send_y = 960, 1467
    if root_before is not None:
        for n in root_before.iter('node'):
            if n.attrib.get('content-desc') == 'Send request':
                b = n.attrib.get('bounds', '')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                if m:
                    send_x = (int(m.group(1)) + int(m.group(3))) // 2
                    send_y = (int(m.group(2)) + int(m.group(4))) // 2
                break

    run_adb(['shell', 'input', 'tap', str(send_x), str(send_y)])
    
    # Poll for response stabilization (up to 12s, check every 500ms)
    elapsed = 0.0
    stable_cycles = 0
    last_text = ''
    root = None
    focus = ''
    
    time.sleep(1.0) # initial wait for model / DB
    
    for _ in range(20):
        focus = get_current_focus()
        if 'dev.metis.agent' not in focus and focus:
            # External app opened (e.g. dialer or clock)
            break
        root = get_ui_dump()
        nodes = parse_response_nodes(root, phrase)
        curr_text = ' '.join(n['text'] for n in nodes)
        if curr_text and curr_text == last_text:
            stable_cycles += 1
            if stable_cycles >= 2:
                break
        elif curr_text:
            last_text = curr_text
            stable_cycles = 1
        time.sleep(0.5)

    latency_ms = int((time.time() - t0) * 1000)
    
    # Save screenshot
    ss_filename = f'{build_name}_{pid}.png'
    ss_path = os.path.join(temp_dir, ss_filename)
    # Binary screencap
    try:
        cmd = f'adb exec-out screencap -p > "{ss_path}"'
        subprocess.run(cmd, shell=True, timeout=10)
    except Exception as e:
        print(f'Screenshot failed: {e}')
        
    # Classify
    if root is None:
        root = get_ui_dump()
    nodes = parse_response_nodes(root, phrase)
    
    resp_class, detail, full_text = classify_response(nodes, focus, phrase)
    
    # Dismiss proposal or cancel if visible
    # Look for Cancel button
    if root is not None:
        for n in root.iter('node'):
            if n.attrib.get('text') == 'Cancel':
                b = n.attrib.get('bounds', '')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
                if m:
                    cx = (int(m.group(1)) + int(m.group(3))) // 2
                    cy = (int(m.group(2)) + int(m.group(4))) // 2
                    run_adb(['shell', 'input', 'tap', str(cx), str(cy)])
                    time.sleep(0.3)
                break
                
    if 'dev.metis.agent' not in focus and focus:
        # Press back to dismiss external app
        run_adb(['shell', 'input', 'keyevent', '4'])
        time.sleep(0.3)
        run_adb(['shell', 'input', 'keyevent', '4'])
        
    sanitized_text = sanitize_text(full_text.replace('\n', ' -- '))
    sanitized_detail = sanitize_text(detail.replace('\n', ' '))
    
    print(f'   -> Class: {resp_class} | Detail: {sanitized_detail} | Latency: {latency_ms}ms')
    
    return {
        'id': pid,
        'phrase': phrase,
        'build': build_name,
        'class': resp_class,
        'detail': sanitized_detail,
        'latency_ms': latency_ms,
        'response_text': sanitized_text,
        'screenshot': ss_path
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build', required=True, help='Build name: main, pr, pr-fix')
    parser.add_argument('--output', required=True, help='Output CSV path')
    parser.add_argument('--ids', default='', help='Comma-separated row IDs (e.g. 1-72 or 10,19,20)')
    parser.add_argument('--mode', choices=['phrases', 'clarification'], default='phrases', help='Run mode')
    args = parser.parse_args()

    temp_dir = os.path.join(tempfile.gettempdir(), 'metis_device_run')
    os.makedirs(temp_dir, exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)

    if args.mode == 'clarification':
        clarify_fieldnames = [
            'id', 'turn1_phrase', 'turn1_class', 'turn1_response',
            'turn2_phrase', 'turn2_class', 'turn2_detail', 'turn2_response',
            'build', 'screenshot_t1', 'screenshot_t2'
        ]
        with open(args.output, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=clarify_fieldnames)
            writer.writeheader()
            for pair_id, t1, t2 in CLARIFICATION_PAIRS:
                row = execute_clarification_pair(pair_id, t1, t2, args.build, temp_dir)
                writer.writerow(row)
                f.flush()
        print(f'Done! Saved clarification rows to {args.output}')
        return

    target_ids = []
    if args.ids:
        for part in args.ids.split(','):
            if '-' in part:
                s, e = part.split('-')
                target_ids.extend(range(int(s), int(e) + 1))
            else:
                target_ids.append(int(part))
    else:
        target_ids = list(range(1, 73))

    # Read existing rows if file exists
    existing_rows = {}
    if os.path.exists(args.output) and args.build != 'pr-fix':
        with open(args.output, 'r', encoding='utf-8') as f:
            r = csv.DictReader(f)
            for row in r:
                existing_rows[int(row['id'])] = row

    fieldnames = ['id', 'phrase', 'build', 'class', 'detail', 'latency_ms', 'response_text', 'screenshot']

    if args.build == 'pr-fix':
        # Append mode
        with open(args.output, 'a', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            for pid in target_ids:
                if pid not in ALL_PHRASES:
                    continue
                phrase = ALL_PHRASES[pid]
                row = execute_phrase(pid, phrase, args.build, temp_dir)
                writer.writerow(row)
                f.flush()
    else:
        for pid in target_ids:
            if pid not in ALL_PHRASES:
                print(f'Skipping unknown id {pid}')
                continue
            phrase = ALL_PHRASES[pid]
            row = execute_phrase(pid, phrase, args.build, temp_dir)
            existing_rows[pid] = row
            # Save progress after each row
            with open(args.output, 'w', newline='', encoding='utf-8') as f:
                writer = csv.DictWriter(f, fieldnames=fieldnames)
                writer.writeheader()
                for k in sorted(existing_rows.keys()):
                    writer.writerow(existing_rows[k])

    print(f'Done! Saved {len(target_ids)} rows to {args.output}')


if __name__ == '__main__':
    main()


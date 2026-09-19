import re
import subprocess
import sys
import xml.etree.ElementTree as ET

subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/trx-ui.xml"], check=True, stdout=subprocess.DEVNULL)
xml = subprocess.check_output(["adb", "exec-out", "cat", "/sdcard/trx-ui.xml"])
for node in ET.fromstring(xml).iter("node"):
    expected=sys.argv[1]
    actual=node.get("text", "")
    matches=actual==expected if "--case-sensitive" in sys.argv else actual.casefold()==expected.casefold()
    if matches:
        bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
        if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            subprocess.run(["adb", "shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2)], check=True)
            sys.exit(0)
sys.exit(1)

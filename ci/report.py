"""Turns UI-test output into GitHub annotations: those are public through the REST API, unlike logs and artifacts."""
import glob,re,os
def note(title,text,level="error"):
    text=text.strip()[:3500].replace('%','%25').replace('\r','').replace('\n','%0A')
    print(f"::{level} title={title}::{text or '(empty)'}")
log=open('ui-tests.log',encoding='utf-8',errors='replace').read() if os.path.exists('ui-tests.log') else ''
keys=[l for l in log.splitlines() if re.search(r'FAILED|Tests? run|No tests|INSTRUMENTATION|crashed|Exception|BUILD|What went wrong|> ',l)]
note("Gradle connectedDebugAndroidTest","\n".join(keys[-40:]) or log[-3000:],"warning")
for f in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml',recursive=True):
    x=open(f,encoding='utf-8',errors='replace').read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*>(.*?)</failure>',x,re.S):
        note("Test failed: "+m.group(1),re.sub(r'&#10;|&#13;','\n',m.group(2))[:3000])
    if '<failure' not in x: note("Test results "+os.path.basename(f),"\n".join(re.findall(r'<testcase name="([^"]+)"',x)),"notice")
if os.path.exists('logcat.txt'):
    lines=open('logcat.txt',encoding='utf-8',errors='replace').read().splitlines()
    for i,l in enumerate(lines):
        if 'FATAL EXCEPTION' in l: note("App crash (logcat)","\n".join(lines[i:i+30]))
    errs=[l for l in lines if re.match(r'[EW]/(Solo|TestRunner|SmokeRunner|AndroidRuntime|Tilt)',l)]
    if errs: note("App errors (logcat)","\n".join(errs[-30:]),"warning")
shots=sorted(os.listdir('build/screenshots')) if os.path.isdir('build/screenshots') else []
note("Screenshots",", ".join(shots) or "none","notice")

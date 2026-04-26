import os
import re

res_dir = "/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260221another-notes-app/app/src/main/res"

count = 0
for d in os.listdir(res_dir):
    # Skip the base 'values' and the translated 'values-vi'
    if d.startswith("values-") and d != "values-vi":
        xml_path = os.path.join(res_dir, d, "strings.xml")
        if os.path.exists(xml_path):
            with open(xml_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Remove the VIP block if it was added
            pattern = r'\s*<!-- VIP Features -->\s*<string name="vip_action_watch_ad">[^<]*</string>\s*<string name="vip_ad_not_ready_title">[^<]*</string>\s*<string name="vip_ad_not_ready_message">[^<]*</string>'
            new_content = re.sub(pattern, '', content)
            
            if new_content != content:
                with open(xml_path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                count += 1

print(f"Cleaned {count} strings.xml files.")

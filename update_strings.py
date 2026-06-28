import os
import re

res_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "res")

strings_to_add = """
    <!-- VIP Features -->
    <string name="vip_action_watch_ad">Watch Ad (Get 3 Days VIP)</string>
    <string name="vip_ad_not_ready_title">Hold on a second</string>
    <string name="vip_ad_not_ready_message">The ad is not ready yet. Please try again in about 1 minute!</string>
"""

count = 0
for d in os.listdir(res_dir):
    if d.startswith("values-"):
        xml_path = os.path.join(res_dir, d, "strings.xml")
        if os.path.exists(xml_path):
            with open(xml_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Check if already added
            if "vip_action_watch_ad" not in content:
                # Find </resources> and insert before it
                content = re.sub(r'</resources>', strings_to_add + '\n</resources>', content)
                
                with open(xml_path, 'w', encoding='utf-8') as f:
                    f.write(content)
                count += 1

print(f"Updated {count} strings.xml files.")

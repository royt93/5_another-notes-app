import os
import re

res_dir = os.path.dirname(os.path.abspath(__file__))

count = 0
for d in os.listdir(res_dir):
    if d.startswith("values"):
        xml_path = os.path.join(res_dir, d, "strings.xml")
        if os.path.exists(xml_path):
            with open(xml_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # For English/Fallback
            if "Watch Ad (Get 3 Days VIP)" in content:
                content = content.replace("Watch Ad (Get 3 Days VIP)", "Watch Ad (Get 3 Days Premium)")
                
                with open(xml_path, 'w', encoding='utf-8') as f:
                    f.write(content)
                count += 1
            # For Vietnamese
            elif "Xem quảng cáo (Nhận VIP 3 ngày)" in content:
                content = content.replace("Xem quảng cáo (Nhận VIP 3 ngày)", "Xem quảng cáo (Nhận 3 ngày Premium)")
                
                with open(xml_path, 'w', encoding='utf-8') as f:
                    f.write(content)
                count += 1

print(f"Fixed {count} strings.xml files.")

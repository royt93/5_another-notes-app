import os
import re

res_dir = os.path.dirname(os.path.abspath(__file__))

translations = {
    "base": "The ad is not ready yet. Please try again later!",
    "vi": "Quảng cáo chưa sẵn sàng. Vui lòng thử lại sau!",
    "ro": "Anunțul nu este gata încă. Te rugăm să încerci din nou mai târziu!",
    "ru": "Реклама еще не готова. Пожалуйста, повторите попытку позже!",
    "it": "L'annuncio non è ancora pronto. Riprova più tardi!",
    "cs": "Reklama ještě není připravena. Zkuste to prosím znovu později!",
    "ja": "広告の準備がまだできていません。後でもう一度お試しください！",
    "el": "Η διαφήμιση δεν είναι ακόμα έτοιμη. Παρακαλώ δοκιμάστε ξανά αργότερα!",
    "da": "Annoncen er ikke klar endnu. Prøv venligst igen senere!",
    "ms": "Iklan belum sedia. Sila cuba lagi nanti!",
    "pl": "Reklama nie jest jeszcze gotowa. Spróbuj ponownie później!",
    "sv": "Annonsen är inte klar än. Vänligen försök igen senare!",
    "sk": "Reklama ešte nie je pripravená. Skúste to znova neskôr!",
    "tr": "Reklam henüz hazır değil. Lütfen daha sonra tekrar deneyin!",
    "th": "โฆษณายังไม่พร้อม โปรดลองอีกครั้งในภายหลัง!",
    "fi": "Mainos ei ole vielä valmis. Yritä uudelleen myöhemmin!",
    "id": "Iklan belum siap. Silakan coba lagi nanti!",
    "fr": "La publicité n'est pas encore prête. Veuillez réessayer plus tard !",
    "es": "El anuncio aún no está listo. Por favor, inténtalo de nuevo más tarde.",
    "hr": "Oglas još nije spreman. Molimo pokušajte ponovo kasnije!",
    "hu": "A hirdetés még nem áll készen. Kérjük, próbáld újra később!",
    "nl": "De advertentie is nog niet klaar. Probeer het later opnieuw!",
    "bg": "Рекламата все още не е готова. Моля, опитайте отново по-късно!",
    "nb": "Annonsen er ikke klar ennå. Prøv igjen senere!",
    "hi": "विज्ञापन अभी तैयार नहीं है। कृपया बाद में पुनः प्रयास करें!",
    "de": "Die Werbung ist noch nicht bereit. Bitte versuchen Sie es später erneut!",
    "ko": "광고가 아직 준비되지 않았습니다. 나중에 다시 시도해주세요!",
    "ar": "الإعلان ليس جاهزًا بعد. يرجى المحاولة مرة أخرى لاحقًا!",
    "pt": "O anúncio ainda não está pronto. Por favor, tente novamente mais tarde!",
    "zh": "广告尚未准备好。请稍后重试！",
    "uk": "Реклама ще не готова. Будь ласка, спробуйте знову пізніше!"
}

count = 0
for d in os.listdir(res_dir):
    if d == "values" or d.startswith("values-"):
        # Determine language key
        if d == "values":
            lang_key = "base"
        else:
            lang_key = d.replace("values-", "")
        
        # We only update if we have a translation for this specific language folder
        # (ignoring values-night, values-w600dp etc if they don't map to a language)
        if lang_key in translations:
            xml_path = os.path.join(res_dir, d, "strings.xml")
            if os.path.exists(xml_path):
                with open(xml_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                new_msg = translations[lang_key].replace("'", "\\'").replace("&", "&amp;")
                pattern = r'<string name="vip_ad_not_ready_message">[^<]*</string>'
                replacement = f'<string name="vip_ad_not_ready_message">{new_msg}</string>'
                
                new_content = re.sub(pattern, replacement, content)
                
                if new_content != content:
                    with open(xml_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    count += 1

print(f"Updated vip_ad_not_ready_message in {count} strings.xml files.")

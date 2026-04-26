import os
import re

res_dir = "/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260221another-notes-app/app/src/main/res"

translations = {
    "ro": ("Vizionează anunț (Primești 3 zile Premium)", "Așteaptă o secundă", "Anunțul nu este gata încă. Te rugăm să încerci din nou în aproximativ 1 minut!"),
    "ru": ("Смотреть рекламу (Получить 3 дня Premium)", "Подождите секунду", "Реклама еще не готова. Пожалуйста, повторите попытку примерно через 1 минуту!"),
    "it": ("Guarda l'annuncio (Ottieni 3 giorni Premium)", "Aspetta un secondo", "L'annuncio non è ancora pronto. Riprova tra circa 1 minuto!"),
    "cs": ("Sledovat reklamu (Získejte 3 dny Premium)", "Počkejte chvíli", "Reklama ještě není připravena. Zkuste to prosím znovu asi za 1 minutu!"),
    "ja": ("広告を見る (3日間のPremiumを獲得)", "少々お待ちください", "広告の準備がまだできていません。約1分後にもう一度お試しください！"),
    "el": ("Δείτε διαφήμιση (Λάβετε 3 ημέρες Premium)", "Περιμένετε ένα δευτερόλεπτο", "Η διαφήμιση δεν είναι ακόμα έτοιμη. Παρακαλώ δοκιμάστε ξανά σε περίπου 1 λεπτό!"),
    "da": ("Se annonce (Få 3 dages Premium)", "Vent et øjeblik", "Annoncen er ikke klar endnu. Prøv venligst igen om cirka 1 minut!"),
    "ms": ("Tonton Iklan (Dapatkan 3 Hari Premium)", "Tunggu sebentar", "Iklan belum sedia. Sila cuba lagi dalam masa kira-kira 1 minit!"),
    "pl": ("Obejrzyj reklamę (Zdobądź 3 dni Premium)", "Poczekaj chwilę", "Reklama nie jest jeszcze gotowa. Spróbuj ponownie za około 1 minutę!"),
    "vi": ("Xem quảng cáo (Nhận 3 ngày Premium)", "Đợi một chút", "Quảng cáo chưa sẵn sàng. Vui lòng thử lại sau khoảng 1 phút!"),
    "sv": ("Se annons (Få 3 dagars Premium)", "Vänta en sekund", "Annonsen är inte klar än. Vänligen försök igen om cirka 1 minut!"),
    "sk": ("Pozrieť reklamu (Získajte 3 dni Premium)", "Počkajte chvíľu", "Reklama ešte nie je pripravená. Skúste to znova asi za 1 minútu!"),
    "tr": ("Reklam İzle (3 Günlük Premium Kazan)", "Bir saniye bekle", "Reklam henüz hazır değil. Lütfen yaklaşık 1 dakika sonra tekrar deneyin!"),
    "th": ("ดูโฆษณา (รับ Premium 3 วัน)", "รอสักครู่", "โฆษณายังไม่พร้อม โปรดลองอีกครั้งในอีกประมาณ 1 นาที!"),
    "fi": ("Katso mainos (Hanki 3 päivän Premium)", "Odota hetki", "Mainos ei ole vielä valmis. Yritä uudelleen noin 1 minuutin kuluttua!"),
    "id": ("Tonton Iklan (Dapatkan 3 Hari Premium)", "Tunggu sebentar", "Iklan belum siap. Silakan coba lagi dalam sekitar 1 menit!"),
    "fr": ("Regarder la pub (Obtenez 3 jours Premium)", "Attendez une seconde", "La publicité n'est pas encore prête. Veuillez réessayer dans environ 1 minute !"),
    "es": ("Ver anuncio (Obtén 3 días de Premium)", "Espera un segundo", "El anuncio aún no está listo. Por favor, inténtalo de nuevo en aproximadamente 1 minuto."),
    "hr": ("Pogledaj oglas (Ostvari 3 dana Premium)", "Pričekajte trenutak", "Oglas još nije spreman. Molimo pokušajte ponovo za oko 1 minutu!"),
    "hu": ("Hirdetés megtekintése (3 nap Premium)", "Várj egy percet", "A hirdetés még nem áll készen. Kérjük, próbáld újra körülbelül 1 perc múlva!"),
    "nl": ("Bekijk advertentie (Krijg 3 dagen Premium)", "Wacht even", "De advertentie is nog niet klaar. Probeer het over ongeveer 1 minuut opnieuw!"),
    "bg": ("Гледайте реклама (Вземете 3 дни Premium)", "Изчакайте малко", "Рекламата все още не е готова. Моля, опитайте отново след около 1 минута!"),
    "nb": ("Se annonse (Få 3 dager Premium)", "Vent et øyeblikk", "Annonsen er ikke klar ennå. Prøv igjen om ca. 1 minutt!"),
    "hi": ("विज्ञापन देखें (3 दिन का Premium पाएं)", "एक पल प्रतीक्षा करें", "विज्ञापन अभी तैयार नहीं है। कृपया लगभग 1 मिनट बाद पुनः प्रयास करें!"),
    "de": ("Werbung ansehen (3 Tage Premium erhalten)", "Warten Sie eine Sekunde", "Die Werbung ist noch nicht bereit. Bitte versuchen Sie es in etwa 1 Minute erneut!"),
    "ko": ("광고 보기 (3일 Premium 획득)", "잠시만 기다려주세요", "광고가 아직 준비되지 않았습니다. 약 1분 후에 다시 시도해주세요!"),
    "ar": ("شاهد الإعلان (احصل على 3 أيام Premium)", "انتظر لحظة", "الإعلان ليس جاهزًا بعد. يرجى المحاولة مرة أخرى بعد حوالي دقيقة واحدة!"),
    "pt": ("Ver anúncio (Obtenha 3 dias de Premium)", "Espere um segundo", "O anúncio ainda não está pronto. Por favor, tente novamente em cerca de 1 minuto!"),
    "zh": ("观看广告 (获取3天 Premium)", "稍等片刻", "广告尚未准备好。请在约1分钟后重试！"),
    "uk": ("Дивитись рекламу (Отримати 3 дні Premium)", "Зачекайте хвилинку", "Реклама ще не готова. Будь ласка, спробуйте знову приблизно через 1 хвилину!")
}

count = 0
for lang_code, trans in translations.items():
    folder_name = f"values-{lang_code}"
    xml_path = os.path.join(res_dir, folder_name, "strings.xml")
    
    if os.path.exists(xml_path):
        with open(xml_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        if "vip_action_watch_ad" not in content:
            # Escape strings for XML
            btn_text = trans[0].replace("'", "\\'").replace("&", "&amp;")
            title_text = trans[1].replace("'", "\\'").replace("&", "&amp;")
            msg_text = trans[2].replace("'", "\\'").replace("&", "&amp;")
            
            str_block = f"""
    <!-- VIP Features -->
    <string name="vip_action_watch_ad">{btn_text}</string>
    <string name="vip_ad_not_ready_title">{title_text}</string>
    <string name="vip_ad_not_ready_message">{msg_text}</string>
"""
            # Insert before </resources>
            content = re.sub(r'</resources>', str_block + '</resources>', content)
            
            with open(xml_path, 'w', encoding='utf-8') as f:
                f.write(content)
            count += 1

print(f"Translated and added strings to {count} language files.")

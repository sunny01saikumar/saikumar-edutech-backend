import urllib.request
import urllib.parse
import json

targets = [
    'Samantha_Ruth_Prabhu', 'Kajal_Aggarwal', 'Raashii_Khanna', 'Rashmika_Mandanna',
    'Deepika_Padukone', 'Alia_Bhatt', 'Katrina_Kaif', 'Tamannaah_Bhatia', 'Kriti_Sanon',
    'Shraddha_Kapoor', 'Kiara_Advani', 'Pooja_Hegde', 'Keerthy_Suresh', 'Mrunal_Thakur',
    'Sreeleela', 'Narendra_Modi', 'Virat_Kohli', 'MS_Dhoni', 'Shah_Rukh_Khan',
    'Prabhas', 'Allu_Arjun', 'Pawan_Kalyan', 'Mahesh_Babu', 'Ram_Charan',
    'N._T._Rama_Rao_Jr.', 'Rajinikanth', 'Elon_Musk'
]

results = {}

for t in targets:
    url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{urllib.parse.quote(t)}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'NexusAI/1.0 (admin@saikumar-nexus.com)'})
        res = urllib.request.urlopen(req, timeout=5)
        data = json.loads(res.read().decode())
        img = data.get('originalimage', {}).get('source') or data.get('thumbnail', {}).get('source')
        if img:
            img_clean = urllib.parse.unquote(img.split('?')[0])
            proxied = f"https://wsrv.nl/?url={urllib.parse.quote(img_clean)}&w=1024&output=jpg"
            try:
                req2 = urllib.request.Request(proxied, headers={'User-Agent': 'Mozilla/5.0'})
                res2 = urllib.request.urlopen(req2, timeout=5)
                print(f"case \"{t}\": return \"{proxied}\"; // OK {res2.status}")
                results[t] = proxied
            except Exception as e2:
                print(f"// {t} PROXY FAIL ({e2}) -> raw: {img_clean}")
                results[t] = img_clean
    except Exception as e:
        print(f"// {t} WIKI FAIL ({e})")

print("\n\nTOTAL SUCCESSFUL:", len(results))

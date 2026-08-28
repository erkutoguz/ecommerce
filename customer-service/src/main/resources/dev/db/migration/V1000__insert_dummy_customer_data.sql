INSERT INTO customers (
    id,
    name,
    email,
    phone,
    status,
    updated_at,
    created_at
) VALUES
      ('c0000000-0000-0000-0000-000000000001', 'Ahmet Yilmaz', 'ahmet.yilmaz@example.com', '+905321000001', 'ACTIVE',   '2026-08-10 14:20:00', '2026-04-02 09:15:00'),
      ('c0000000-0000-0000-0000-000000000002', 'Ayse Demir', 'ayse.demir@example.com', '+905321000002', 'ACTIVE',     '2026-08-12 11:10:00', '2026-04-05 10:30:00'),
      ('c0000000-0000-0000-0000-000000000003', 'Mehmet Kaya', 'mehmet.kaya@example.com', '+905321000003', 'ACTIVE',   '2026-08-08 16:40:00', '2026-04-12 12:00:00'),
      ('c0000000-0000-0000-0000-000000000004', 'Zeynep Aydin', 'zeynep.aydin@example.com', NULL, 'ACTIVE',            '2026-08-14 09:45:00', '2026-04-19 14:20:00'),
      ('c0000000-0000-0000-0000-000000000005', 'Can Yilmaz', 'can.yilmaz@example.com', '+905321000005', 'ACTIVE',     '2026-08-17 12:30:00', '2026-05-01 08:50:00'),
      ('c0000000-0000-0000-0000-000000000006', 'Elif Sahin', 'elif.sahin@example.com', '+905321000006', 'ACTIVE',     '2026-08-11 17:15:00', '2026-05-05 13:40:00'),
      ('c0000000-0000-0000-0000-000000000007', 'Burak Arslan', 'burak.arslan@example.com', NULL, 'ACTIVE',            '2026-08-18 10:05:00', '2026-05-08 11:10:00'),
      ('c0000000-0000-0000-0000-000000000008', 'Selin Koc', 'selin.koc@example.com', '+905321000008', 'ACTIVE',       '2026-08-20 14:55:00', '2026-05-15 15:30:00'),
      ('c0000000-0000-0000-0000-000000000009', 'Mert Cetin', 'mert.cetin@example.com', '+905321000009', 'ACTIVE',     '2026-08-21 09:35:00', '2026-05-21 10:10:00'),
      ('c0000000-0000-0000-0000-000000000010', 'Deniz Aksoy', 'deniz.aksoy@example.com', NULL, 'ACTIVE',              '2026-08-19 13:20:00', '2026-05-24 09:25:00'),
      ('c0000000-0000-0000-0000-000000000011', 'Ece Yildiz', 'ece.yildiz@example.com', '+905321000011', 'ACTIVE',     '2026-08-22 17:40:00', '2026-06-01 12:45:00'),
      ('c0000000-0000-0000-0000-000000000012', 'Kerem Polat', 'kerem.polat@example.com', '+905321000012', 'ACTIVE',   '2026-08-23 08:50:00', '2026-06-04 16:10:00'),
      ('c0000000-0000-0000-0000-000000000013', 'Melis Gunes', 'melis.gunes@example.com', '+905321000013', 'INACTIVE', '2026-08-25 18:00:00', '2026-04-11 10:00:00'),
      ('c0000000-0000-0000-0000-000000000014', 'Ozan Kurt', 'ozan.kurt@example.com', '+905321000014', 'ACTIVE',       '2026-08-24 11:45:00', '2026-06-10 13:00:00'),
      ('c0000000-0000-0000-0000-000000000015', 'Irem Tas', 'irem.tas@example.com', NULL, 'ACTIVE',                    '2026-08-26 10:20:00', '2026-06-18 14:35:00'),
      ('c0000000-0000-0000-0000-000000000016', 'Emre Acar', 'emre.acar@example.com', '+905321000016', 'INACTIVE',     '2026-08-18 19:30:00', '2026-05-30 09:00:00'),
      ('c0000000-0000-0000-0000-000000000017', 'Derya Kocak', 'derya.kocak@example.com', '+905321000017', 'ACTIVE',   '2026-08-27 16:15:00', '2026-07-01 11:20:00'),
      ('c0000000-0000-0000-0000-000000000018', 'Tolga Eren', 'tolga.eren@example.com', NULL, 'INACTIVE',              '2026-08-22 15:10:00', '2026-03-22 08:40:00');


INSERT INTO customer_addresses (
    id,
    customer_id,
    full_address,
    city,
    country
) VALUES
      ('a0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Ataturk Mahallesi 101 Sokak No 12 Kadikoy', 'Istanbul', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'Bahcelievler Mahallesi No 28 Cankaya', 'Ankara', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002', 'Alsancak Mahallesi 1456 Sokak No 8 Konak', 'Izmir', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000003', 'Nilufer Mahallesi Papatya Sokak No 19', 'Bursa', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000003', 'Cumhuriyet Mahallesi Deniz Sokak No 4', 'Balikesir', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000004', 'Lara Mahallesi Barinaklar Bulvari No 55', 'Antalya', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000004', 'Konyaalti Mahallesi Sahil Caddesi No 17', 'Antalya', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000005', 'Tepebasi Mahallesi Universite Caddesi No 41', 'Eskisehir', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000005', 'Yenibaglar Mahallesi No 73', 'Eskisehir', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000006', 'Selcuklu Mahallesi Mevlana Caddesi No 22', 'Konya', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000011', 'c0000000-0000-0000-0000-000000000007', 'Ortahisar Mahallesi Uzun Sokak No 16', 'Trabzon', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000012', 'c0000000-0000-0000-0000-000000000008', 'Seyhan Mahallesi Inonu Caddesi No 31', 'Adana', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000013', 'c0000000-0000-0000-0000-000000000008', 'Cukurova Mahallesi Toros Caddesi No 9', 'Adana', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000014', 'c0000000-0000-0000-0000-000000000009', 'Atakum Mahallesi Sahil Yolu No 14', 'Samsun', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000015', 'c0000000-0000-0000-0000-000000000010', 'Merkezefendi Mahallesi Gazi Bulvari No 61', 'Denizli', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000016', 'c0000000-0000-0000-0000-000000000010', 'Pamukkale Mahallesi No 18', 'Denizli', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000017', 'c0000000-0000-0000-0000-000000000011', 'Muratpasa Mahallesi Cumhuriyet Caddesi No 32', 'Antalya', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000018', 'c0000000-0000-0000-0000-000000000012', 'Karsiyaka Mahallesi Cemal Gursel Caddesi No 24', 'Izmir', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000019', 'c0000000-0000-0000-0000-000000000013', 'Besiktas Mahallesi Barbaros Bulvari No 18', 'Istanbul', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000020', 'c0000000-0000-0000-0000-000000000014', 'Cankaya Mahallesi Tunali Hilmi Caddesi No 45', 'Ankara', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000021', 'c0000000-0000-0000-0000-000000000014', 'Eryaman Mahallesi 5 Cadde No 10', 'Ankara', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000022', 'c0000000-0000-0000-0000-000000000015', 'Bornova Mahallesi Universite Caddesi No 36', 'Izmir', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000023', 'c0000000-0000-0000-0000-000000000016', 'Osmangazi Mahallesi Fevzi Cakmak Caddesi No 11', 'Bursa', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000024', 'c0000000-0000-0000-0000-000000000017', 'Maltepe Mahallesi Bagdat Caddesi No 90', 'Istanbul', 'Turkey'),
      ('a0000000-0000-0000-0000-000000000025', 'c0000000-0000-0000-0000-000000000017', 'Golbasi Mahallesi Incek Bulvari No 15', 'Ankara', 'Turkey'),

      ('a0000000-0000-0000-0000-000000000026', 'c0000000-0000-0000-0000-000000000018', 'Kordon Mahallesi Cumhuriyet Bulvari No 42', 'Izmir', 'Turkey');
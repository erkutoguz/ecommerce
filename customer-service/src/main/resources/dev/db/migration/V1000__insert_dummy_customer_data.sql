-- Dummy customers for local development

INSERT INTO customers (
    id,
    name,
    email,
    phone,
    status,
    updated_at,
    created_at
) VALUES
      (
          '10000000-0000-0000-0000-000000000001',
          'Ahmet Yilmaz',
          'ahmet.yilmaz@example.com',
          '+905301112233',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000002',
          'Mehmet Kaya',
          'mehmet.kaya@example.com',
          '+905322223344',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000003',
          'Ayse Demir',
          'ayse.demir@example.com',
          '+905333334455',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000004',
          'Zeynep Celik',
          'zeynep.celik@example.com',
          '+905344445566',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000005',
          'Can Aydin',
          'can.aydin@example.com',
          '+905355556677',
          'INACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000006',
          'Elif Sahin',
          'elif.sahin@example.com',
          '+905366667788',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000007',
          'Burak Arslan',
          'burak.arslan@example.com',
          '+905377778899',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000008',
          'Selin Koc',
          'selin.koc@example.com',
          '+905388889900',
          'INACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000009',
          'Emre Yildiz',
          'emre.yildiz@example.com',
          '+905399990011',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000010',
          'Derya Kaplan',
          'derya.kaplan@example.com',
          NULL,
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000011',
          'Mert Ozkan',
          'mert.ozkan@example.com',
          '+905411112233',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000012',
          'Ceren Aksoy',
          'ceren.aksoy@example.com',
          '+905422223344',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000013',
          'Onur Polat',
          'onur.polat@example.com',
          NULL,
          'INACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000014',
          'Ece Kurt',
          'ece.kurt@example.com',
          '+905433334455',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          '10000000-0000-0000-0000-000000000015',
          'Kerem Tas',
          'kerem.tas@example.com',
          '+905444445566',
          'ACTIVE',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      );


-- Dummy customer addresses

INSERT INTO customer_addresses (
    id,
    customer_id,
    full_address,
    city,
    country
) VALUES
      (
          '20000000-0000-0000-0000-000000000001',
          '10000000-0000-0000-0000-000000000001',
          'Ataturk Mah. Cumhuriyet Cad. No: 12',
          'Istanbul',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000002',
          '10000000-0000-0000-0000-000000000001',
          'Bahcelievler Mah. Inonu Sok. No: 8',
          'Ankara',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000003',
          '10000000-0000-0000-0000-000000000002',
          'Alsancak Mah. Kibris Sehitleri Cad. No: 45',
          'Izmir',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000004',
          '10000000-0000-0000-0000-000000000003',
          'Nilufer Mah. FSM Bulvari No: 27',
          'Bursa',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000005',
          '10000000-0000-0000-0000-000000000004',
          'Konyaalti Mah. Akdeniz Bulvari No: 14',
          'Antalya',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000006',
          '10000000-0000-0000-0000-000000000004',
          'Cankaya Mah. Ataturk Cad. No: 31',
          'Ankara',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000007',
          '10000000-0000-0000-0000-000000000005',
          'Tepebasi Mah. Porsuk Bulvari No: 5',
          'Eskisehir',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000008',
          '10000000-0000-0000-0000-000000000006',
          'Kadikoy Mah. Moda Cad. No: 18',
          'Istanbul',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000009',
          '10000000-0000-0000-0000-000000000007',
          'Seyhan Mah. Ataturk Cad. No: 63',
          'Adana',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000010',
          '10000000-0000-0000-0000-000000000007',
          'Yeni Mah. Gazi Cad. No: 21',
          'Mersin',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000011',
          '10000000-0000-0000-0000-000000000008',
          'Ortahisar Mah. Uzun Sok. No: 10',
          'Trabzon',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000012',
          '10000000-0000-0000-0000-000000000009',
          'Selcuklu Mah. Alaaddin Bulvari No: 42',
          'Konya',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000013',
          '10000000-0000-0000-0000-000000000010',
          'Atakum Mah. Sahil Cad. No: 25',
          'Samsun',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000014',
          '10000000-0000-0000-0000-000000000011',
          'Melikgazi Mah. Talas Bulvari No: 17',
          'Kayseri',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000015',
          '10000000-0000-0000-0000-000000000012',
          'Sehitkamil Mah. Gazimuhtar Cad. No: 36',
          'Gaziantep',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000016',
          '10000000-0000-0000-0000-000000000012',
          'Bostanli Mah. Cemal Gursel Cad. No: 72',
          'Izmir',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000017',
          '10000000-0000-0000-0000-000000000014',
          'Besiktas Mah. Barbaros Bulvari No: 51',
          'Istanbul',
          'Turkey'
      ),
      (
          '20000000-0000-0000-0000-000000000018',
          '10000000-0000-0000-0000-000000000015',
          'Cankaya Mah. Tunali Hilmi Cad. No: 22',
          'Ankara',
          'Turkey'
      );
# Municipal Source Register

These are official discovery pages for the first seven jurisdictions. They are
not evidence that every linked file has been downloaded, reviewed or indexed.
The ingestion workflow must store the exact source URL and document status, and
retrieval must exclude drafts and sources awaiting review.

| Jurisdiction | Official source | Initial corpus target |
| --- | --- | --- |
| Bogotá D.C. | [District legal system: Decree 555 of 2021](https://www.bogotajuridica.gov.co/sisjur/normas/Norma1.jsp?i=119582) | POT text, applicable amendments including Decree 466 of 2024, current maps, and legal-status instruments |
| Pasto | [Municipal POT document repository](https://www.pasto.gov.co/index.php/pot) | Agreement 004 of 2015, technical support documents, maps, and subsequent amendments |
| Medellín | [POT agreement and regulatory archive](https://www.medellin.gov.co/es/transparencia/plan-de-ordenamiento-territorial-de-medellin/) | Agreement 48 of 2014, amendments, implementing acts, current maps, and parcel-specific consultation references |
| Cali | [POT Agreement 0373 of 2014 documents](https://web1.cali.gov.co/publicaciones.php?dPrint=1&id=52108) | Agreement, technical support, official maps, and later acts affecting validity |
| Bucaramanga | [POT archive](https://www.bucaramanga.gov.co/bucaramanga-avanza/archivo-pot/) | Agreement 011 of 2014, 2014-2027 supporting material, maps, and later amendments |
| Barranquilla | [Current POT document register](https://barranquilla.gov.co/plan-de-ordenamiento-territorial) | Decree 0212 of 2014, complementary acts, current risk studies, maps, and annexes |
| Cartagena | [Current POT and regulatory register](https://seguimientopot.cartagena.gov.co/documentos-del-pot-vigente) | Decree 0977 of 2001 and applicable modifications; treat the replacement POT as a draft until formally adopted and effective |

National sources that may apply across jurisdictions must be indexed separately
and linked to their issuing authority. The current official repositories for the
[Decree 1077 of 2015](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=77216)
and [NSR-10 amendments](https://www.minvivienda.gov.co/normativa?search_api_fulltext=nsr+10)
must be checked for amendments and effective dates before ingestion.

## Coverage policy

Do not claim nationwide municipal coverage from this initial seven-city register.
Each jurisdiction needs its own applicable POT, amendments, maps and supporting
rules. Store publication date, effective interval, issuing authority, official
URL, checksum and review state with each indexed document. A municipality with
no verified documents must return a no-context result instead of inheriting
another city's rules.

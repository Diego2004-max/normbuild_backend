# Official Municipal Source Register

**Review date:** 2026-11-03. These URLs are discovery and verification sources;
they are not proof that a document has been downloaded, reviewed or indexed.
Only reviewed, effective primary legal instruments should be ingested. Preserve
the source URL and the exact PDF checksum with the ingestion record or audit log.

| Jurisdiction | Official source | Current-source handling |
| --- | --- | --- |
| Bogotá D.C. | [District legal system: Decree 555 of 2021](https://www.bogotajuridica.gov.co/sisjur/normas/Norma1.jsp?i=119582) | Verify [Decree 466 of 2024](https://www.bogotajuridica.gov.co/sisjur/normas/Norma1.jsp?i=170937), [Decree 466 of 2026](https://www.bogotajuridica.gov.co/sisjur/normas/Norma1.jsp?i=194741), subsequent effective acts and current parcel maps. A baseline 2021 PDF alone is not a consolidated corpus. |
| Pasto | [Municipal POT repository](https://www.pasto.gov.co/index.php/pot) | The repository includes Agreement 004 of 2015 and supporting files. Check amendments and the applicable map for the parcel before indexing a rule as current. |
| Medellín | [POT regulatory archive](https://www.medellin.gov.co/es/transparencia/plan-de-ordenamiento-territorial-de-medellin/) | Use Agreement 48 of 2014 and effective amendments; the city's POT parcel lookup is essential for location-specific rules. Keep review proposals separate from adopted acts. |
| Cali | [Municipal POT documents](https://www.cali.gov.co/planeacion/publicaciones/52108/documento-plan-de-ordenamiento-territorial/) and the [official IDESC POT file archive](https://idesc.cali.gov.co/download/pot_2014/) | The City reported a judicial extension for Agreement 0373 of 2014 through 2027-07-09. The IDESC archive provides thematic annexes and the Circular Aclaratoria, not a consolidated replacement for the full Agreement and parcel maps. Verify the extension and subsequent acts at import time. |
| Bucaramanga | [POT archive](https://www.bucaramanga.gov.co/bucaramanga-avanza/archivo-pot/) | The archive identifies Agreement 011 of 2014 and the 2014–2027 POT. Track any later effective amendment; evaluation or revision materials are not regulations. |
| Barranquilla | [Current POT document register](https://barranquilla.gov.co/plan-de-ordenamiento-territorial) | Start from Decree 0212 of 2014 and verify later modifications and map updates, including the risk-map updates and 2026 cartographic acts listed by the District. |
| Cartagena | [Current POT document register](https://seguimientopot.cartagena.gov.co/documentos-del-pot-vigente) | The official register publishes Decree 0977 of 2001 in two volumes. Review both, then verify later effective acts and parcel cartography. Keep replacement POT review documents out of the binding corpus until adopted and effective. |

National sources that apply across jurisdictions must be indexed separately and
linked to their issuing authority. The Ministry hosts a Decree 1077 consolidated
PDF dated 2026-02-16; [Decree 381 of 2026](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=275556)
later modifies it, so both the snapshot and intervening acts need review. Check
the official consolidated [Decree 1077 of 2015](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=77216)
and [NSR-10 materials](https://www.minvivienda.gov.co/normativa?search_api_fulltext=nsr+10),
including amendments and effective dates, before ingestion.

The downloadable Decree 0926 of 2010 is only the adoption instrument and does
not include the complete technical NSR-10 text. It is not enough to support
structural design calculations. Obtain the complete authorized technical
annex and verify its amendments before indexing or presenting structural
requirements as sourced.

## Import and review policy

1. Download the PDF from an official HTTPS `.gov.co` source and retain its URL.
2. Confirm the instrument is adopted, published, in force, and relevant to the
   jurisdiction. Record amendment, suspension, court decision and effective-date
   context in its metadata before import.
3. Import only selectable-text PDFs through the secured PDF ingestion endpoint.
   Scanned PDFs require OCR and human verification; the current importer rejects
   them instead of indexing unreliable text.
4. Check the API's `/api/regulations/coverage` result after ingestion. Coverage
   reports actual indexed sections and distinct source URLs; a listed city with
   zero sections has no indexed official source and must not receive a legal
   conclusion from another jurisdiction's corpus.

## Coverage policy

This initial register covers seven prioritized cities only. It does not claim
complete municipal coverage, complete amendments, parcel-level applicability,
or legal advice. RAG citations must point to the exact official source URL that
was ingested. Jurisdiction aliases are canonicalized at query and ingestion
time so accents and common city-name variants do not split the same corpus.

The catalog in `municipal-document-catalog.json` identifies official base POT
PDFs that can be downloaded for review. A base POT is not sufficient by itself
to determine an individual parcel's permissible construction. Current
amendments, parcel-specific zoning and cartography must also be checked.
## Newly verified legal-status and mapping sources

The official Cartagena POT register reports that proceeding
[13-001-33-31-004-2023-00203-00](https://seguimientopot.cartagena.gov.co/normativa/sentencia-13-001-33-31-004-2023-00203-00-2023-216)
provisionally suspended the phrase “y obligatorias” in Article 291 and Articles
304, 305 and 306 of Decree 0977 of 2001. This is a material legal-status
qualification for any retrieval from the 2001 POT. Obtain the primary ruling,
confirm its operative scope and current status, and encode the qualification
before those provisions can support an answer. Do not infer that the rest of
the POT was suspended.

The District of Barranquilla's
[current POT register](https://barranquilla.gov.co/plan-de-ordenamiento-territorial)
identifies Decree 0893 of 2024 (18 October 2024) as adopting a risk study and
updating POT maps U-10 (mass movement), U-11 (flooding) and U-12 (general
risks). The decree and updated official map layers are necessary for risk
screening; the 2014 POT PDF alone cannot establish parcel-level risk.

Cartagena's
[official parcel information system (MIDAS)](https://seguimientopot.cartagena.gov.co/geodata-midas)
publishes parcel-level and territorial layers, including land use, risk and
licenses. A text-only POT corpus cannot derive those parcel-specific facts.
Require the relevant location and consult the official spatial layer before
claiming a parcel passes a zoning or risk check.

The current machine can reach some Cali thematic annexes, but direct PDF
requests for the full Cali Agreement, Barranquilla Decree 0212, and both
Cartagena Decree 0977 volumes returned HTTP 403. Their catalog entries remain
discovery records only; the importer has not indexed these files. Retrieve the
PDFs through the official portals from a deployment host that can access them,
then verify checksums, text extraction, legal status and coverage before use.

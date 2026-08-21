-- V10: Service Catalog — add display/order columns and seed the "Our Services"
-- content from the public landing page so the admin Service Catalog can manage it.

ALTER TABLE services ADD COLUMN IF NOT EXISTS icon VARCHAR(255) DEFAULT 'fa-solid fa-briefcase';
ALTER TABLE services ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- Seed the landing page's Our Services content (idempotent).
INSERT INTO services (name, description, category, icon, sort_order, is_active, created_at, updated_at)
SELECT name, description, category, icon, sort_order, TRUE, NOW(), NOW()
FROM (VALUES
  -- ECC tab
  ('Environmental Compliance Certificate (ECC)',
   'The Environmental Compliance Certificate or ECC refers to the document issued by the DENR/Environmental Management Board (EMB) that allows the project to proceed to the next stage of project planning, which is the acquisition of approvals from other government agencies and LGUs, after which the project can start implementation.' || E'\n\n' ||
   'It certifies that the proponent has complied with the requirements of the Environmental Impact Statement (EIS) system and that the proposed project will not cause a significant negative impact on the environment. It also certifies that the proponent is committed to implement its approved Environment Management Plan. Requirements for ECC application depend on the type and location of project being developed.',
   'ECC', 'fa-solid fa-leaf', 1),
  -- CNC tab
  ('Certificate of Non-Coverage (CNC)',
   'The Certificate of Non-Coverage is a document issued by the DENR/Environmental Management Board (EMB) certifying that, based on the submitted project description, the project is not covered by the EIS (Environmental Impact Statement) system and is not required to secure an ECC. This covers projects which are not critical to the environment.',
   'CNC', 'fa-solid fa-circle-check', 2),
  -- Other Services tab (list)
  ('Environmental Impact Assessment (EIA)',
   'A systematic evaluation of the potential environmental effects of a proposed project, informing the ECC decision.',
   'Other Services', 'fa-solid fa-toolbox', 3),
  ('Discharge Permit (DP)',
   'Authorization to discharge wastewater or effluents within regulated limits, tailored to facility operations.',
   'Other Services', 'fa-solid fa-toolbox', 4),
  ('Permit for Operation of Air Pollutant Sources and Central Installation',
   'Clearance covering emission sources and central installations in compliance with air quality standards.',
   'Other Services', 'fa-solid fa-toolbox', 5),
  ('Hazardous Waste Generator ID',
   'Registration identifying the facility as a hazardous waste generator under regulatory requirements.',
   'Other Services', 'fa-solid fa-toolbox', 6),
  ('Feasibility Studies for Businesses',
   'Technical and commercial studies evaluating the viability of a proposed business or project.',
   'Other Services', 'fa-solid fa-toolbox', 7)
) AS seed(name, description, category, icon, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = seed.name);
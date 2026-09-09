-- V37: align the audience display label with the shipped UI wording.
-- V35 wired the forms to read display_label, and the announcement surfaces
-- (badge + audience select) have always said "Company-wide", while the V2
-- seed stored "Company". The DB row is canonical now, so the seed follows
-- the UI.
UPDATE dropdown_values v
   SET display_label = 'Company-wide'
  FROM dropdown_categories c
 WHERE v.category_id = c.id
   AND c.name = 'audience'
   AND v.value = 'COMPANY'
   AND v.display_label = 'Company';

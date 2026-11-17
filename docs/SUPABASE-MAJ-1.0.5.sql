alter table public.fiches add column if not exists contact text check (contact is null or char_length(contact) <= 24);
grant select (contact) on public.fiches to anon;

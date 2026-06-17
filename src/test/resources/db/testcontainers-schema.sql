-- DERIVED from tech-refresh/supabase/migrations 0001_init.sql and
-- 0003_status_events.sql for Testcontainers. Only the contacts and
-- status_events tables this service reads are kept. Transformations vs.
-- the source: auth.users FK references become plain uuid columns, the
-- auth.uid() defaults and all RLS/policy statements are removed (Supabase
-- auth schema is unavailable in a bare Postgres container). The column
-- shapes and the status-event trigger are preserved verbatim.

create table contacts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  name text not null,
  status text not null default 'Contacted'
    check (status in ('Contacted', 'Applied', 'Interviewing', 'Offer', 'Rejected')),
  role text,
  link text,
  note text,
  date date,
  next_action text,
  next_action_date date,
  created_at timestamptz not null default now()
);

create table status_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  contact_id uuid not null references contacts (id) on delete cascade,
  status text not null,
  created_at timestamptz not null default now()
);

create index status_events_user_created on status_events (user_id, created_at);
create or replace function record_contact_status()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' or new.status is distinct from old.status then
    insert into status_events (user_id, contact_id, status)
    values (new.user_id, new.id, new.status);
  end if;
  return new;
end;
$$;

create trigger contacts_status_event
  after insert or update of status on contacts
  for each row execute function record_contact_status();

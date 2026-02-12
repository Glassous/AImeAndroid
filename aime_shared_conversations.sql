-- Create aime_shared_conversations table
create table aime_shared_conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  title text,
  model text,
  messages jsonb
);

-- Enable Row Level Security (RLS)
alter table aime_shared_conversations enable row level security;

-- Policy: Allow public read access (for viewing shared conversations)
create policy "Enable read access for all users"
on aime_shared_conversations for select
to public
using (true);

-- Policy: Allow anonymous insert access (for app users to upload conversations)
create policy "Enable insert access for anonymous users"
on aime_shared_conversations for insert
to anon
with check (true);

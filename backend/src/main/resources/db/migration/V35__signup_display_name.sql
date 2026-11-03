-- BUG-168 (T58). A sign-up's link carries the name chosen at that sign-up, as it carries the password (V34): the owner of
-- an address someone else signed up with first ends up with their own name, not the first registrant's.
ALTER TABLE email_token ADD COLUMN display_name text;

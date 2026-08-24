-- SoloMapling adds hundreds of headless characters to the normal channel
-- player storage. Keep the channel accessible to real players.
UPDATE game_config
SET config_value = '2000'
WHERE config_code = 'channel_capacity';

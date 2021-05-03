# hubitat-modern-forms-fan

Hubitat driver for Modern Forms combined fan + light device

This driver automatically creates a separate child dimmer device for the
light attached to the fan, to simplify dashboards and smart home automation
like Google Home (which does not support combined fan and light devices).

If local physical controls are used to change the state of the device, this
driver will automatically pick up the changes via polling (by default, it polls
every 30 seconds, which can be customized in the device preferences).

# Setup

1) In Hubitat, navigate to Drivers Code, then click New Driver.

2) Open the file `modern-forms-fan.groovy` from this repository, copy its contents,
and paste them into the Hubitat New Driver editor window. Click Save.

3) Navigate to Devices, then click Add Virtual Device.

4) Click the text field for "Device Name" and enter a name, e.g. "Living Room Fan".

5) Click the drop-down next to "Type" and select "Modern Forms Fan and Light".

6) Click "Save Device".

7) In the new device window, click "Fan IP Address" and enter the IP address of
your Modern Forms fan device.

NOTE: Ensure your device has a static DHCP IP address, or Hubitat will lose
connection if your device is assigned a new IP address.

8) Click "Save Preferences".

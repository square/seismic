Change Log
==========

Version 1.0.3 *(2021-09-28)*
----------------------------

 * New: `start()` overload which accepts a sensor start delay. Default is fastest.
 * Fix: Clear sample queue when `stop()` is called. This prevents false positives when restarting.


Version 1.0.2 *(2015-08-04)*
----------------------------

 * Added 'setSensitivity' method for configuring shake sensitivity.


Version 1.0.1 *(2014-12-16)*
----------------------------

 * Remove excessive `Math.sqrt` calculation.


Version 1.0.0 *(2012-09-05)*
----------------------------

Initial release.

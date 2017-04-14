import express from 'express';
// import validate from 'express-validation';
// import paramValidation from '../../config/param-validation';
import scannerCtrl from '../controllers/scanner.controller';

const router = express.Router(); // eslint-disable-line new-cap

router.route('/')
  /** GET /api/users - Get list of users */
  .get(scannerCtrl.list);

router.route('/count')
  .get(scannerCtrl.count);

router.route('/monthData')
  .get(scannerCtrl.monthData);

router.route('/:scannerId')
  /** GET /api/users/:userId - Get user */
  .get(scannerCtrl.get);

/** Load user when API with userId route parameter is hit */
router.param('scannerId', scannerCtrl.load);

export default router;

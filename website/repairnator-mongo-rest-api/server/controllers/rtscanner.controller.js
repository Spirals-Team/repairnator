import RtScanner from '../models/rtscanner.model';

/**
 * Load rtScanner and append to req.
 */
function load(req, res, next, id) {
  RtScanner.get(id)
    .then((rtScanner) => {
      req.rtScanner = rtScanner; // eslint-disable-line no-param-reassign
      return next();
    })
    .catch(e => next(e));
}

/**
 * Get rtScanner
 * @returns {RtScanner}
 */
function get(req, res) {
  return res.json(req.rtScanner);
}

/**
 * Get rtScanner list.
 * @property {number} req.query.skip - Number of users to be skipped.
 * @property {number} req.query.limit - Limit number of users to be returned.
 * @returns {RtScanner[]}
 */
function list(req, res, next) {
  const { limit = 50, skip = 0 } = req.query;
  RtScanner.list({ limit, skip })
    .then(result => res.json(result))
    .catch(e => next(e));
}

export default {
  load,
  get,
  list,
};

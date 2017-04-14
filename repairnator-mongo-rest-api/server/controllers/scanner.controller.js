import Scanner from '../models/scanner.model';

/**
 * Load user and append to req.
 */
function load(req, res, next, id) {
  Scanner.get(id)
    .then((user) => {
      req.user = user; // eslint-disable-line no-param-reassign
      return next();
    })
    .catch(e => next(e));
}

/**
 * Get user
 * @returns {User}
 */
function get(req, res) {
  return res.json(req.scanner);
}

/**
 * Get user list.
 * @property {number} req.query.skip - Number of users to be skipped.
 * @property {number} req.query.limit - Limit number of users to be returned.
 * @returns {User[]}
 */
function list(req, res, next) {
  const { limit = 50, skip = 0 } = req.query;
  Scanner.list({ limit, skip })
    .then(users => res.json(users))
    .catch(e => next(e));
}

function count(req, res, next) {
  Scanner.count()
    .then(result => res.json(result))
    .catch(e => next(e));
}

function monthData(req, res, next) {
  const now = new Date();
  const month = now.getMonth();
  const year = now.getFullYear();

  Scanner.getMonthData(month, year).then(result => res.json(result)).catch(e => next(e));
}

export default { load, get, list, count, monthData };

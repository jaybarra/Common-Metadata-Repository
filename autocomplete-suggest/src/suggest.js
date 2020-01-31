// suggest.js

const FlexSearch = require('flexsearch');

const LOG = require('./logger');

/**
 * @typedef {object} SuggestionCollection
 * @property {string} type The type of suggestion, equating to a facet in CMR search
 * @property {Array.<string>} suggestions Array of strings
 */

/**
 * Returns a list of suggestions and the original query
 *
 * @param {RedisClient} redis - Redis connection client
 * @param {string} type - Facet type to get suggestions for
 * @param {string} query - The string to search for
 * @param {object} [opts] - Options
 * @param {number} [opts.limit = 10] quantity limit
 * @returns {Promise<SuggestionCollection>}
 */
const suggest = async (redis, type, query, opts) => {
  const updatedOpts = {
    limit: 10,
    ...opts,
  };

  let flexsearchIndex;
  let suggestions = [];

  try {
    flexsearchIndex = await redis.getAsync(type);

    if (!flexsearchIndex) {
      LOG.error(`No redis key [${type}] found`);
    } else {
      const index = new FlexSearch('speed');
      index.import(flexsearchIndex);
      suggestions = index.search(query, updatedOpts.limit);
    }
  } catch (err) {
    const { message } = err;
    LOG.error(`An error occurred accessing redis: ${message}`);
  }

  return {
    suggestions,
    type,
  };
};

module.exports = suggest;

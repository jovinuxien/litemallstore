import React from 'react';

const LEFT_PAGE = 'LEFT';
const RIGHT_PAGE = 'RIGHT';

export type PaginationData = {
  currentPage: number;
  totalPages: number;
  pageLimit: number;
  totalRecords: number;
};

type PagingProps = {
  sizing?: string; // Optional, default to '' if not provided
  alignment?: string; // Optional, default to '' if not provided
  totalRecords?: number; // Optional, default to 0 if not provided
  pageLimit?: number; // Optional, default to 30 if not provided
  pageNeighbours?: number; // Optional, default to 0 if not provided

  onPageChanged?: (data: PaginationData) => void;
};
type PagingState = {
  currentPage: number;
};

const range = (from: number, to: number, step: number = 1): number[] => {
  if (typeof from !== 'number' || typeof to !== 'number' || typeof step !== 'number') {
    throw new Error('Invalid input: from,  to, and step must be numbers');
  }
  const range: number[] = [];
  let i: number = from;
  while (i <= to) {
    range.push(i);
    i += step;
  }

  return range;
};

class Paging extends React.Component<PagingProps, PagingState> {
  state: PagingState;

  sizing: string;
  alignment: string;
  pageLimit: number;
  totalRecords: number;
  pageNeighbours: number;
  totalPages: number;

  constructor(props: PagingProps) {
    super(props);

    // Destructuring with fallback values
    const { totalRecords = 0, pageLimit = 30, pageNeighbours = 0, sizing = '', alignment = '' } = props;

    // Assigning values to instance variables with types
    this.sizing = typeof sizing === 'string' ? sizing : '';
    this.alignment = typeof alignment === 'string' ? alignment : '';
    this.pageLimit = typeof pageLimit === 'number' ? pageLimit : 30;
    this.totalRecords = typeof totalRecords === 'number' ? totalRecords : 0;

    this.pageNeighbours = typeof pageNeighbours === 'number' ? Math.max(0, Math.min(pageNeighbours, 2)) : 0;

    this.totalPages = Math.ceil(this.totalRecords / this.pageLimit);

    this.state = { currentPage: 1 };
  }

  componentDidMount() {
    this.gotoPage(1);
  }

  gotoPage = (page: number) => {
    const { onPageChanged = f => f } = this.props;

    const currentPage = Math.max(0, Math.min(page, this.totalPages));
    const paginationData = {
      currentPage,
      totalPages: this.totalPages,
      pageLimit: this.pageLimit,
      totalRecords: this.totalRecords,
    };

    this.setState({ currentPage }, () => onPageChanged(paginationData));
  };

  handleClick = (page: number, evt: React.MouseEvent<HTMLAnchorElement>) => {
    evt.preventDefault();
    this.gotoPage(page);
  };

  handleMoveLeft = (evt: React.MouseEvent<HTMLAnchorElement>) => {
    evt.preventDefault();
    this.gotoPage(this.state.currentPage - this.pageNeighbours * 2 - 1);
  };

  handleMoveRight = (evt: React.MouseEvent<HTMLAnchorElement>) => {
    evt.preventDefault();
    this.gotoPage(this.state.currentPage + this.pageNeighbours * 2 + 1);
  };

  fetchPageNumbers = () => {
    const totalPages = this.totalPages;
    const currentPage = this.state.currentPage;
    const pageNeighbours = this.pageNeighbours;

    const totalNumbers = this.pageNeighbours * 2 + 3;
    const totalBlocks = totalNumbers + 2;

    if (totalPages > totalBlocks) {
      let pages = [];

      const leftBound = currentPage - pageNeighbours;
      const rightBound = currentPage + pageNeighbours;
      const beforeLastPage = totalPages - 1;

      const startPage = leftBound > 2 ? leftBound : 2;
      const endPage = rightBound < beforeLastPage ? rightBound : beforeLastPage;

      pages = range(startPage, endPage);

      const pagesCount = pages.length;
      const singleSpillOffset = totalNumbers - pagesCount - 1;

      const leftSpill = startPage > 2;
      const rightSpill = endPage < beforeLastPage;

      const leftSpillPage = LEFT_PAGE;
      const rightSpillPage = RIGHT_PAGE;

      if (leftSpill && !rightSpill) {
        const extraPages = range(startPage - singleSpillOffset, startPage - 1);
        pages = [leftSpillPage, ...extraPages, ...pages];
      } else if (!leftSpill && rightSpill) {
        const extraPages = range(endPage + 1, endPage + singleSpillOffset);
        pages = [...pages, ...extraPages, rightSpillPage];
      } else if (leftSpill && rightSpill) {
        pages = [leftSpillPage, ...pages, rightSpillPage];
      }

      return [1, ...pages, totalPages];
    }

    return range(1, totalPages);
  };

  render() {
    if (!this.totalRecords) return null;

    if (this.totalPages === 1) return null;

    const { currentPage } = this.state;
    const pages = this.fetchPageNumbers();

    return (
      <nav aria-label='Page navigation'>
        <ul className={`pagination ${this.sizing} ${this.alignment}`}>
          {pages.map((page, index) => {
            if (page === LEFT_PAGE)
              return (
                <li key={index} className='page-item'>
                  <button
                    className='page-link'
                    aria-label='Previous'
                    onClick={(evt: React.MouseEvent<HTMLButtonElement>) => this.handleMoveLeft(evt as unknown as React.MouseEvent<HTMLAnchorElement>)}
                  >
                    <span aria-hidden='true'>&laquo;</span>
                    <span className='sr-only'>Previous</span>
                  </button>
                </li>
              );

            if (page === RIGHT_PAGE)
              return (
                <li key={index} className='page-item'>
                  <button
                    className='page-link'
                    aria-label='Next'
                    onClick={(evt: React.MouseEvent<HTMLButtonElement>) => this.handleMoveRight(evt as unknown as React.MouseEvent<HTMLAnchorElement>)}
                  >
                    <span aria-hidden='true'>&raquo;</span>
                    <span className='sr-only'>Next</span>
                  </button>
                </li>
              );

            return (
              <li key={index} className={`page-item${currentPage === page ? ' active' : ''}`}>
                <button
                  className='page-link'
                  onClick={(e: React.MouseEvent<HTMLButtonElement>) => this.handleClick(page as number, e as unknown as React.MouseEvent<HTMLAnchorElement>)}
                >
                  {page}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    );
  }
}

export default Paging;

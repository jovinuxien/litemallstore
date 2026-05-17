import * as React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Breadcrumb, BreadcrumbItem } from 'reactstrap';
import routes from '../../../routes';

interface BreadcrumbsProps {
  // Add any additional props if needed
}
const findRouteName = (url: string) => routes[url];

const getPaths = (pathname: string) => {
  const paths = ['/'];

  if (pathname === '/') {
    return paths;
  }

  pathname.split('/').reduce((prev, curr, index) => {
    const currPath = `${prev}/${curr}`;
    paths.push(currPath);
    return currPath;
  });
  return paths;
};

const BreadcrumbsItem = ({ match, ...rest }) => {
  const routeName = findRouteName(match.url);
  if (routeName) {
    return match.isExact ? (
      <BreadcrumbItem active>{routeName}</BreadcrumbItem>
    ) : (
      <BreadcrumbItem>
        <Link to={match.url || ''}>{routeName}</Link>
      </BreadcrumbItem>
    );
  }
  return null;
};

/* const Breadcrumbs = ({ location: { pathname }, match, ...rest }) => {
  const paths = getPaths(pathname);
  const items = paths.map((path, i) => <Route key={i++} path={path} element={<BreadcrumbItem />} />);
  return <Breadcrumb>{items}</Breadcrumb>;
}; */
const Breadcrumbs: React.FC<BreadcrumbsProps> = () => {
  const location = useLocation();
  const paths = getPaths(location.pathname);

  return (
    <Breadcrumb>
      {paths.map((path, index) => {
        const routeName = findRouteName(path);
        const isLast = index === paths.length - 1;

        if (!routeName) return null;

        return (
          <BreadcrumbItem key={path} active={isLast}>
            {isLast ? routeName : <Link to={path}>{routeName}</Link>}
          </BreadcrumbItem>
        );
      })}
    </Breadcrumb>
  );
};

const BreadcrumbWrapper: React.FC = () => {
  return (
    <div>
      <Breadcrumbs />
    </div>
  );
};

export default BreadcrumbWrapper;
